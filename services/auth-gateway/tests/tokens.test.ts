import { TokenStore } from "../src/tokens";

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
});
