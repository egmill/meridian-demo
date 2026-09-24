import request from "supertest";
import { createApp } from "../src/app";

describe("POST /auth/login", () => {
  it("returns a token for valid credentials and MFA code", async () => {
    const res = await request(createApp())
      .post("/auth/login")
      .send({ username: "jdoe", password: "Meridian!2026", mfaCode: "246810" });

    expect(res.status).toBe(200);
    expect(res.body.token).toBeDefined();
  });
});
