module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  roots: ["<rootDir>/tests"],
  collectCoverageFrom: ["src/**/*.ts", "!src/server.ts"],
  coverageReporters: ["text", "lcov"],
  coverageThreshold: {
    global: { lines: 80 },
  },
};
