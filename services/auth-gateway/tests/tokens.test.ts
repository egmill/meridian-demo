import { TokenStore, TOKEN_TTL_MS } from "../src/tokens";

const START = 1_700_000_000_000;

function storeWithClock(initial = START) {
  let now = initial;
  const store = new TokenStore(() => now);
  return { store, advance: (ms: number) => (now += ms), set: (t: number) => (now = t) };
}

describe("TokenStore", () => {
  it("issues a token that is valid immediately", () => {
    const store = new TokenStore();
    const { token } = store.issue("jdoe");
    expect(store.isValid(token)).toBe(true);
  });

  it("rejects an unknown token", () => {
    const store = new TokenStore();
    expect(store.isValid("not-a-real-token")).toBe(false);
  });

  it("issues a 48-hex-character token with expiry exactly TTL after issue", () => {
    const { store } = storeWithClock();
    const { token, expiresAt } = store.issue("jdoe");
    expect(token).toMatch(/^[0-9a-f]{48}$/);
    expect(expiresAt).toBe(START + TOKEN_TTL_MS);
  });

  it("uses a 15 minute TTL", () => {
    expect(TOKEN_TTL_MS).toBe(15 * 60 * 1000);
  });

  it("issues distinct tokens for repeated logins", () => {
    const { store } = storeWithClock();
    const a = store.issue("jdoe").token;
    const b = store.issue("jdoe").token;
    expect(a).not.toBe(b);
    expect(store.usernameFor(a)).toBe("jdoe");
    expect(store.usernameFor(b)).toBe("jdoe");
  });

  it("resolves the username for a valid token", () => {
    const { store } = storeWithClock();
    const { token } = store.issue("jdoe");
    expect(store.usernameFor(token)).toBe("jdoe");
  });

  it("returns undefined username for an unknown token", () => {
    const { store } = storeWithClock();
    expect(store.usernameFor("nope")).toBeUndefined();
  });

  it("returns undefined username for the empty token", () => {
    const { store } = storeWithClock();
    expect(store.usernameFor("")).toBeUndefined();
  });

  it("is still valid one millisecond before expiry", () => {
    const { store, advance } = storeWithClock();
    const { token } = store.issue("jdoe");
    advance(TOKEN_TTL_MS - 1);
    expect(store.isValid(token)).toBe(true);
    expect(store.usernameFor(token)).toBe("jdoe");
  });

  test.failing(
    "SUSPECTED BUG: token is still accepted exactly at its expiry timestamp (rule: invalid at or after expiry)",
    () => {
      const { store, advance } = storeWithClock();
      const { token } = store.issue("jdoe");
      advance(TOKEN_TTL_MS);
      expect(store.isValid(token)).toBe(false);
    },
  );

  it("is invalid one millisecond after expiry", () => {
    const { store, advance } = storeWithClock();
    const { token } = store.issue("jdoe");
    advance(TOKEN_TTL_MS + 1);
    expect(store.isValid(token)).toBe(false);
    expect(store.usernameFor(token)).toBeUndefined();
  });
});
