import { createHash, timingSafeEqual } from "crypto";

export interface User {
  username: string;
  displayName: string;
  passwordSha256: string;
  mfaCode: string; // demo only: stands in for a TOTP secret
}

const sha256 = (value: string) => createHash("sha256").update(value).digest("hex");

const USERS: User[] = [
  {
    username: "jdoe",
    displayName: "Jordan Doe",
    passwordSha256: sha256("Meridian!2026"),
    mfaCode: "246810",
  },
];

export function findUser(username: string): User | undefined {
  return USERS.find((u) => u.username === username);
}

export function passwordMatches(user: User, password: string): boolean {
  const a = Buffer.from(sha256(password), "hex");
  const b = Buffer.from(user.passwordSha256, "hex");
  return a.length === b.length && timingSafeEqual(a, b);
}
