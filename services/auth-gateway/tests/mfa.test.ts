import { MfaLockout, MAX_FAILED_ATTEMPTS, LOCKOUT_MS } from "../src/mfa";

const START = 1_700_000_000_000;

function lockoutWithClock(initial = START) {
  let now = initial;
  const lockout = new MfaLockout(() => now);
  return { lockout, advance: (ms: number) => (now += ms) };
}

function failTimes(lockout: MfaLockout, username: string, n: number) {
  for (let i = 0; i < n; i++) lockout.recordFailure(username);
}

describe("MfaLockout", () => {
  it("locks after 5 attempts for 15 minutes", () => {
    expect(MAX_FAILED_ATTEMPTS).toBe(5);
    expect(LOCKOUT_MS).toBe(15 * 60 * 1000);
  });

  it("is not locked for a user with no failures", () => {
    const { lockout } = lockoutWithClock();
    expect(lockout.isLocked("jdoe")).toBe(false);
    expect(lockout.lockedUntil("jdoe")).toBeNull();
    expect(lockout.remainingAttempts("jdoe")).toBe(MAX_FAILED_ATTEMPTS);
  });

  it("decrements remaining attempts on each failure", () => {
    const { lockout } = lockoutWithClock();
    lockout.recordFailure("jdoe");
    expect(lockout.remainingAttempts("jdoe")).toBe(4);
    lockout.recordFailure("jdoe");
    expect(lockout.remainingAttempts("jdoe")).toBe(3);
  });

  it("is not locked after 4 failures", () => {
    const { lockout } = lockoutWithClock();
    failTimes(lockout, "jdoe", MAX_FAILED_ATTEMPTS - 1);
    expect(lockout.isLocked("jdoe")).toBe(false);
    expect(lockout.lockedUntil("jdoe")).toBeNull();
    expect(lockout.remainingAttempts("jdoe")).toBe(1);
  });

  it("locks on the 5th failure until now + 15 minutes", () => {
    const { lockout } = lockoutWithClock();
    failTimes(lockout, "jdoe", MAX_FAILED_ATTEMPTS);
    expect(lockout.isLocked("jdoe")).toBe(true);
    expect(lockout.lockedUntil("jdoe")).toBe(START + LOCKOUT_MS);
    expect(lockout.remainingAttempts("jdoe")).toBe(0);
  });

  it("never reports negative remaining attempts", () => {
    const { lockout } = lockoutWithClock();
    failTimes(lockout, "jdoe", MAX_FAILED_ATTEMPTS + 3);
    expect(lockout.remainingAttempts("jdoe")).toBe(0);
  });

  it("tracks failures independently per user", () => {
    const { lockout } = lockoutWithClock();
    failTimes(lockout, "jdoe", MAX_FAILED_ATTEMPTS);
    expect(lockout.isLocked("jdoe")).toBe(true);
    expect(lockout.isLocked("asmith")).toBe(false);
    expect(lockout.remainingAttempts("asmith")).toBe(MAX_FAILED_ATTEMPTS);
  });

  it("remains locked one millisecond before the window ends", () => {
    const { lockout, advance } = lockoutWithClock();
    failTimes(lockout, "jdoe", MAX_FAILED_ATTEMPTS);
    advance(LOCKOUT_MS - 1);
    expect(lockout.isLocked("jdoe")).toBe(true);
  });

  it("unlocks exactly when the window ends", () => {
    const { lockout, advance } = lockoutWithClock();
    failTimes(lockout, "jdoe", MAX_FAILED_ATTEMPTS);
    advance(LOCKOUT_MS);
    expect(lockout.isLocked("jdoe")).toBe(false);
  });

  test.failing(
    "SUSPECTED BUG: failure counter is not reset when the lockout window ends (rule: counter resets when lockout window ends)",
    () => {
      const { lockout, advance } = lockoutWithClock();
      failTimes(lockout, "jdoe", MAX_FAILED_ATTEMPTS);
      advance(LOCKOUT_MS);
      expect(lockout.remainingAttempts("jdoe")).toBe(MAX_FAILED_ATTEMPTS);
    },
  );

  test.failing(
    "SUSPECTED BUG: a single failure after the lockout window ends re-locks the account immediately",
    () => {
      const { lockout, advance } = lockoutWithClock();
      failTimes(lockout, "jdoe", MAX_FAILED_ATTEMPTS);
      advance(LOCKOUT_MS);
      lockout.recordFailure("jdoe");
      expect(lockout.isLocked("jdoe")).toBe(false);
    },
  );
});
