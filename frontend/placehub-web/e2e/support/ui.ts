import { expect, type Page } from '@playwright/test';

export type Portal = 'student' | 'recruiter' | 'admin';

/** Signs in through the role's login portal and waits for the role home. */
export async function signIn(page: Page, email: string, password: string, portal: Portal) {
  await page.goto(`/login/${portal}`);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  // The portal URL itself contains the role name, so match on the path prefix.
  await expect(page).toHaveURL((url) => url.pathname === `/${portal}` || url.pathname.startsWith(`/${portal}/`));
}

export async function signOut(page: Page) {
  await page.evaluate(() => sessionStorage.clear());
}

export async function expectToast(page: Page, text: string | RegExp) {
  await expect(page.getByRole('status').filter({ hasText: text }).first()).toBeVisible();
}
