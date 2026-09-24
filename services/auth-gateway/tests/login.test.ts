import request from "supertest";
import { createApp } from "../src/app";
import { MfaLockout, MAX_FAILED_ATTEMPTS, LOCKOUT_MS } from "../src/mfa";
import { TokenStore, TOKEN_TTL_MS } from "../src/tokens";

const START = 1_700_000_000_000;
const VALID = { username: "jdoe", password: "Meridian!2026", mfaCode: "246810" };

function appWithClock(initial = START) {
  let now = initial;
  const clock = () => now;
  const tokens = new TokenStore(clock);
  const lockout = new MfaLockout(clock);
  const app = createApp(tokens, lockout);
  return { app, tokens, lockout, advance: (ms: number) => (now += ms) };
}

const login = (app: ReturnType<typeof createApp>, body: unknown) =>
  request(app).post("/auth/login").send(body as object);

describe("POST /auth/login", () => {
  it("returns a token for valid credentials and MFA code", async () => {
    const res = await request(createApp()).post("/auth/login").send(VALID);

    expect(res.status).toBe(200);
    expect(res.body.token).toBeDefined();
  });

  it("returns token, expiry and display name on success", async () => {
    const { app, tokens } = appWithClock();
    const res = await login(app, VALID);

    expect(res.status).toBe(200);
    expect(res.body).toEqual({
      token: expect.stringMatching(/^[0-9a-f]{48}$/),
      expiresAt: START + TOKEN_TTL_MS,
      displayName: "Jordan Doe",
    });
    expect(tokens.usernameFor(res.body.token)).toBe("jdoe");
  });

  describe("input validation", () => {
    it("400 when the body is empty", async () => {
      const res = await request(createApp()).post("/auth/login");
      expect(res.status).toBe(400);
      expect(res.body).toEqual({ error: "username and password are required" });
    });

    it("400 when username is missing", async () => {
      const res = await login(createApp(), { password: "x", mfaCode: "246810" });
      expect(res.status).toBe(400);
      expect(res.body.error).toBe("username and password are required");
    });

    it("400 when password is missing", async () => {
      const res = await login(createApp(), { username: "jdoe", mfaCode: "246810" });
      expect(res.status).toBe(400);
      expect(res.body.error).toBe("username and password are required");
    });

    it("400 when username is not a string", async () => {
      const res = await login(createApp(), { ...VALID, username: 123 });
      expect(res.status).toBe(400);
    });

    it("400 when password is not a string", async () => {
      const res = await login(createApp(), { ...VALID, password: ["Meridian!2026"] });
      expect(res.status).toBe(400);
    });

    it("400 on malformed JSON", async () => {
      const res = await request(createApp())
        .post("/auth/login")
        .set("Content-Type", "application/json")
        .send("{not json");
      expect(res.status).toBe(400);
    });

    it("does not count a validation failure as an MFA failure", async () => {
      const { app, lockout } = appWithClock();
      await login(app, { username: "jdoe" });
      expect(lockout.remainingAttempts("jdoe")).toBe(MAX_FAILED_ATTEMPTS);
    });
  });

  describe("credential failures", () => {
    it("401 with generic error for an unknown user", async () => {
      const res = await login(createApp(), { ...VALID, username: "nobody" });
      expect(res.status).toBe(401);
      expect(res.body).toEqual({ error: "Invalid username or password" });
    });

    it("401 with generic error for a wrong password", async () => {
      const res = await login(createApp(), { ...VALID, password: "wrong" });
      expect(res.status).toBe(401);
      expect(res.body).toEqual({ error: "Invalid username or password" });
    });

    it("uses the same error for unknown user and wrong password", async () => {
      const a = await login(createApp(), { ...VALID, username: "nobody" });
      const b = await login(createApp(), { ...VALID, password: "wrong" });
      expect(a.body).toEqual(b.body);
    });

    it("does not issue a token or count an MFA failure on wrong password", async () => {
      const { app, lockout } = appWithClock();
      const res = await login(app, { ...VALID, password: "wrong" });
      expect(res.body.token).toBeUndefined();
      expect(lockout.remainingAttempts("jdoe")).toBe(MAX_FAILED_ATTEMPTS);
    });

    it("does not reveal the MFA outcome when the password is wrong", async () => {
      const res = await login(createApp(), { ...VALID, password: "wrong", mfaCode: "000000" });
      expect(res.body.error).toBe("Invalid username or password");
      expect(res.body.remainingAttempts).toBeUndefined();
    });
  });

  describe("MFA failures and lockout", () => {
    it("401 with remaining attempts on a wrong MFA code", async () => {
      const { app } = appWithClock();
      const res = await login(app, { ...VALID, mfaCode: "000000" });
      expect(res.status).toBe(401);
      expect(res.body).toEqual({ error: "Invalid MFA code", remainingAttempts: MAX_FAILED_ATTEMPTS - 1 });
    });

    it("401 when the MFA code is missing", async () => {
      const { app } = appWithClock();
      const res = await login(app, { username: VALID.username, password: VALID.password });
      expect(res.status).toBe(401);
      expect(res.body.error).toBe("Invalid MFA code");
      expect(res.body.remainingAttempts).toBe(MAX_FAILED_ATTEMPTS - 1);
    });

    it("rejects a numeric MFA code that equals the string code", async () => {
      const { app } = appWithClock();
      const res = await login(app, { ...VALID, mfaCode: 246810 });
      expect(res.status).toBe(401);
      expect(res.body.error).toBe("Invalid MFA code");
    });

    it("counts down remaining attempts on consecutive failures", async () => {
      const { app } = appWithClock();
      for (let expected = MAX_FAILED_ATTEMPTS - 1; expected >= 1; expected--) {
        const res = await login(app, { ...VALID, mfaCode: "000000" });
        expect(res.status).toBe(401);
        expect(res.body.remainingAttempts).toBe(expected);
      }
    });

    it("still allows login on the 5th attempt after 4 failures", async () => {
      const { app } = appWithClock();
      for (let i = 0; i < MAX_FAILED_ATTEMPTS - 1; i++) {
        await login(app, { ...VALID, mfaCode: "000000" });
      }
      const res = await login(app, VALID);
      expect(res.status).toBe(200);
      expect(res.body.token).toBeDefined();
    });

    it("423 with lockedUntil on the 5th consecutive MFA failure", async () => {
      const { app } = appWithClock();
      for (let i = 0; i < MAX_FAILED_ATTEMPTS - 1; i++) {
        await login(app, { ...VALID, mfaCode: "000000" });
      }
      const res = await login(app, { ...VALID, mfaCode: "000000" });
      expect(res.status).toBe(423);
      expect(res.body).toEqual({
        error: "Account locked after too many failed MFA attempts",
        lockedUntil: START + LOCKOUT_MS,
      });
    });

    it("423 for correct credentials while locked", async () => {
      const { app } = appWithClock();
      for (let i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
        await login(app, { ...VALID, mfaCode: "000000" });
      }
      const res = await login(app, VALID);
      expect(res.status).toBe(423);
      expect(res.body.lockedUntil).toBe(START + LOCKOUT_MS);
      expect(res.body.token).toBeUndefined();
    });

    it("423 even for a wrong password while locked", async () => {
      const { app } = appWithClock();
      for (let i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
        await login(app, { ...VALID, mfaCode: "000000" });
      }
      const res = await login(app, { ...VALID, password: "wrong" });
      expect(res.status).toBe(423);
    });

    it("stays locked one millisecond before the window ends", async () => {
      const { app, advance } = appWithClock();
      for (let i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
        await login(app, { ...VALID, mfaCode: "000000" });
      }
      advance(LOCKOUT_MS - 1);
      const res = await login(app, VALID);
      expect(res.status).toBe(423);
    });

    it("allows login again once the 15 minute window has ended", async () => {
      const { app, advance } = appWithClock();
      for (let i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
        await login(app, { ...VALID, mfaCode: "000000" });
      }
      advance(LOCKOUT_MS);
      const res = await login(app, VALID);
      expect(res.status).toBe(200);
      expect(res.body.token).toBeDefined();
    });

    it("does not lock a different user", async () => {
      const { app, lockout } = appWithClock();
      for (let i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
        await login(app, { ...VALID, mfaCode: "000000" });
      }
      expect(lockout.isLocked("asmith")).toBe(false);
    });

    test.failing(
      "SUSPECTED BUG: failure counter is not reset after a successful login (rule: counter resets after successful login)",
      async () => {
        const { app } = appWithClock();
        await login(app, { ...VALID, mfaCode: "000000" });
        await login(app, { ...VALID, mfaCode: "000000" });
        const ok = await login(app, VALID);
        expect(ok.status).toBe(200);

        const res = await login(app, { ...VALID, mfaCode: "000000" });
        expect(res.body.remainingAttempts).toBe(MAX_FAILED_ATTEMPTS - 1);
      },
    );

    test.failing(
      "SUSPECTED BUG: one wrong MFA code after the lockout window ends re-locks the account instead of allowing 5 fresh attempts",
      async () => {
        const { app, advance } = appWithClock();
        for (let i = 0; i < MAX_FAILED_ATTEMPTS; i++) {
          await login(app, { ...VALID, mfaCode: "000000" });
        }
        advance(LOCKOUT_MS);
        const res = await login(app, { ...VALID, mfaCode: "000000" });
        expect(res.status).toBe(401);
        expect(res.body.remainingAttempts).toBe(MAX_FAILED_ATTEMPTS - 1);
      },
    );
  });
});

describe("GET /auth/validate", () => {
  it("validates a freshly issued token", async () => {
    const { app } = appWithClock();
    const { body } = await login(app, VALID);
    const res = await request(app).get("/auth/validate").set("Authorization", `Bearer ${body.token}`);
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ valid: true, username: "jdoe" });
  });

  it("401 with no Authorization header", async () => {
    const res = await request(createApp()).get("/auth/validate");
    expect(res.status).toBe(401);
    expect(res.body).toEqual({ valid: false });
  });

  it("401 for an unknown token", async () => {
    const res = await request(createApp()).get("/auth/validate").set("Authorization", "Bearer deadbeef");
    expect(res.status).toBe(401);
    expect(res.body).toEqual({ valid: false });
  });

  it("401 for an empty Bearer token", async () => {
    const res = await request(createApp()).get("/auth/validate").set("Authorization", "Bearer ");
    expect(res.status).toBe(401);
    expect(res.body).toEqual({ valid: false });
  });

  it("401 when the token is sent without the Bearer scheme", async () => {
    const { app } = appWithClock();
    const { body } = await login(app, VALID);
    const res = await request(app).get("/auth/validate").set("Authorization", body.token);
    expect(res.status).toBe(401);
    expect(res.body).toEqual({ valid: false });
  });

  it("401 when a non-Bearer scheme is used", async () => {
    const { app } = appWithClock();
    const { body } = await login(app, VALID);
    const res = await request(app).get("/auth/validate").set("Authorization", `Basic ${body.token}`);
    expect(res.status).toBe(401);
  });

  it("401 for a lowercase bearer scheme", async () => {
    const { app } = appWithClock();
    const { body } = await login(app, VALID);
    const res = await request(app).get("/auth/validate").set("Authorization", `bearer ${body.token}`);
    expect(res.status).toBe(401);
  });

  it("still valid one millisecond before expiry", async () => {
    const { app, advance } = appWithClock();
    const { body } = await login(app, VALID);
    advance(TOKEN_TTL_MS - 1);
    const res = await request(app).get("/auth/validate").set("Authorization", `Bearer ${body.token}`);
    expect(res.status).toBe(200);
    expect(res.body.valid).toBe(true);
  });

  test.failing(
    "SUSPECTED BUG: /auth/validate accepts a token exactly at its expiry timestamp (rule: invalid at or after expiry)",
    async () => {
      const { app, advance } = appWithClock();
      const { body } = await login(app, VALID);
      advance(TOKEN_TTL_MS);
      const res = await request(app).get("/auth/validate").set("Authorization", `Bearer ${body.token}`);
      expect(res.status).toBe(401);
    },
  );

  it("401 one millisecond after expiry", async () => {
    const { app, advance } = appWithClock();
    const { body } = await login(app, VALID);
    advance(TOKEN_TTL_MS + 1);
    const res = await request(app).get("/auth/validate").set("Authorization", `Bearer ${body.token}`);
    expect(res.status).toBe(401);
    expect(res.body).toEqual({ valid: false });
  });

  it("does not accept a token issued by a different app instance", async () => {
    const a = appWithClock();
    const b = appWithClock();
    const { body } = await login(a.app, VALID);
    const res = await request(b.app).get("/auth/validate").set("Authorization", `Bearer ${body.token}`);
    expect(res.status).toBe(401);
  });
});

describe("static web UI", () => {
  it("serves the banking UI at /", async () => {
    const res = await request(createApp()).get("/");
    expect(res.status).toBe(200);
    expect(res.headers["content-type"]).toMatch(/text\/html/);
  });

  it("404 for unknown paths", async () => {
    const res = await request(createApp()).get("/does-not-exist");
    expect(res.status).toBe(404);
  });
});
