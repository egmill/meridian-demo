import { randomBytes } from "crypto";

export const TOKEN_TTL_MS = 15 * 60 * 1000;

interface TokenRecord {
  username: string;
  expiresAt: number;
}

export class TokenStore {
  private readonly tokens = new Map<string, TokenRecord>();

  constructor(private readonly now: () => number = Date.now) {}

  issue(username: string): { token: string; expiresAt: number } {
    const token = randomBytes(24).toString("hex");
    const expiresAt = this.now() + TOKEN_TTL_MS;
    this.tokens.set(token, { username, expiresAt });
    return { token, expiresAt };
  }

  isValid(token: string): boolean {
    const record = this.tokens.get(token);
    if (!record) {
      return false;
    }
    return this.now() <= record.expiresAt;
  }

  usernameFor(token: string): string | undefined {
    return this.isValid(token) ? this.tokens.get(token)?.username : undefined;
  }
}
