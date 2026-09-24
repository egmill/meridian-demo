import express, { Request, Response } from "express";
import path from "path";
import { MfaLockout } from "./mfa";
import { TokenStore } from "./tokens";
import { findUser, passwordMatches } from "./users";

export function createApp(
  tokens: TokenStore = new TokenStore(),
  lockout: MfaLockout = new MfaLockout(),
) {
  const app = express();
  app.use(express.json());
  app.use(express.static(path.join(__dirname, "..", "public")));

  app.post("/auth/login", (req: Request, res: Response) => {
    const { username, password, mfaCode } = req.body ?? {};
    if (typeof username !== "string" || typeof password !== "string") {
      return res.status(400).json({ error: "username and password are required" });
    }

    if (lockout.isLocked(username)) {
      return res.status(423).json({
        error: "Account locked after too many failed MFA attempts",
        lockedUntil: lockout.lockedUntil(username),
      });
    }

    const user = findUser(username);
    if (!user || !passwordMatches(user, password)) {
      return res.status(401).json({ error: "Invalid username or password" });
    }

    if (mfaCode !== user.mfaCode) {
      lockout.recordFailure(username);
      if (lockout.isLocked(username)) {
        return res.status(423).json({
          error: "Account locked after too many failed MFA attempts",
          lockedUntil: lockout.lockedUntil(username),
        });
      }
      return res.status(401).json({
        error: "Invalid MFA code",
        remainingAttempts: lockout.remainingAttempts(username),
      });
    }

    const { token, expiresAt } = tokens.issue(username);
    return res.json({ token, expiresAt, displayName: user.displayName });
  });

  app.get("/auth/validate", (req: Request, res: Response) => {
    const header = req.header("authorization") ?? "";
    const token = header.startsWith("Bearer ") ? header.slice(7) : "";
    const username = tokens.usernameFor(token);
    if (!username) {
      return res.status(401).json({ valid: false });
    }
    return res.json({ valid: true, username });
  });

  return app;
}
