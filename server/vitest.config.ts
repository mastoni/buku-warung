import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    env: {
      SERVER_PEPPER: 'test_pepper_for_unit_tests_only'
    }
  }
});
