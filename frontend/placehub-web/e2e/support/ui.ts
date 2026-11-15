import { expect, type Page } from '@playwright/test';

export type Portal = 'student' | 'recruiter' | 'admin';

/** Signs in through the role's login portal and waits for the role home. */
export async function signIn(page: Page, email: string, password: string, portal: Portal) {
  void portal; // v2 has a single login page; per-portal routes arrive in Phase 9.5.
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(new RegExp(`/${portal}(/|$)`));
}

export async function signOut(page: Page) {
  await page.evaluate(() => sessionStorage.clear());
}

export async function expectToast(page: Page, text: string | RegExp) {
  await expect(page.getByRole('status').filter({ hasText: text }).first()).toBeVisible();
}
