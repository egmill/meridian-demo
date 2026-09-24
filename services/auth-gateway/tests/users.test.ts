import { createHash } from "crypto";
import { findUser, passwordMatches, User } from "../src/users";

const sha256 = (v: string) => createHash("sha256").update(v).digest("hex");

const testUser: User = {
  username: "testuser",
  displayName: "Test User",
  passwordSha256: sha256("Correct-Horse-1"),
  mfaCode: "000000",
};

describe("findUser", () => {
  it("returns the demo user by exact username", () => {
    const user = findUser("jdoe");
    expect(user?.username).toBe("jdoe");
    expect(user?.displayName).toBe("Jordan Doe");
    expect(user?.passwordSha256).toBe(sha256("Meridian!2026"));
  });

  it("is case sensitive", () => {
    expect(findUser("JDOE")).toBeUndefined();
  });

  it("returns undefined for an unknown user", () => {
    expect(findUser("nobody")).toBeUndefined();
  });

  it("returns undefined for the empty string", () => {
    expect(findUser("")).toBeUndefined();
  });

  it("does not store plaintext passwords", () => {
    const user = findUser("jdoe")!;
    expect(user.passwordSha256).toMatch(/^[0-9a-f]{64}$/);
    expect(user.passwordSha256).not.toContain("Meridian");
  });
});

describe("passwordMatches", () => {
  it("accepts the correct password", () => {
    expect(passwordMatches(testUser, "Correct-Horse-1")).toBe(true);
  });

  it("rejects a wrong password", () => {
    expect(passwordMatches(testUser, "Wrong-Horse-1")).toBe(false);
  });

  it("rejects a password differing only by case", () => {
    expect(passwordMatches(testUser, "correct-horse-1")).toBe(false);
  });

  it("rejects the empty password", () => {
    expect(passwordMatches(testUser, "")).toBe(false);
  });

  it("rejects the stored hash supplied as the password", () => {
    expect(passwordMatches(testUser, testUser.passwordSha256)).toBe(false);
  });

  it("rejects when the stored hash has a different length", () => {
    const shortHash: User = { ...testUser, passwordSha256: "abcd" };
    expect(passwordMatches(shortHash, "Correct-Horse-1")).toBe(false);
  });
});
