export const MAX_FAILED_ATTEMPTS = 5;
export const LOCKOUT_MS = 15 * 60 * 1000;

interface LockoutState {
  failedAttempts: number;
  lockedUntil: number | null;
}

/**
 * Tracks failed MFA attempts per user and locks the account after too many.
 */
export class MfaLockout {
  private readonly state = new Map<string, LockoutState>();

  constructor(private readonly now: () => number = Date.now) {}

  isLocked(username: string): boolean {
    const s = this.state.get(username);
    return !!s && s.lockedUntil !== null && this.now() < s.lockedUntil;
  }

  lockedUntil(username: string): number | null {
    return this.state.get(username)?.lockedUntil ?? null;
  }

  recordFailure(username: string): void {
    const s = this.stateFor(username);
    s.failedAttempts += 1;
    if (s.failedAttempts >= MAX_FAILED_ATTEMPTS) {
      s.lockedUntil = this.now() + LOCKOUT_MS;
    }
  }

  remainingAttempts(username: string): number {
    return Math.max(0, MAX_FAILED_ATTEMPTS - this.stateFor(username).failedAttempts);
  }

  private stateFor(username: string): LockoutState {
    let s = this.state.get(username);
    if (!s) {
      s = { failedAttempts: 0, lockedUntil: null };
      this.state.set(username, s);
    }
    return s;
  }
}
