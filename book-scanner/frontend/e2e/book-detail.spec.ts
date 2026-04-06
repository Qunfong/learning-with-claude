import { test, expect } from '@playwright/test';
import { BOOKS } from './fixtures/books';

const CLEAN_CODE = BOOKS[0]; // id=1
const PRAGMATIC  = BOOKS[1]; // id=2

test.describe('Book detail page', () => {
  test('displays all book metadata for Clean Code', async ({ page }) => {
    await page.route('/api/books/1', route =>
      route.fulfill({ json: CLEAN_CODE })
    );

    await page.goto('/books/1');

    await expect(page.getByRole('heading', { name: 'Clean Code' })).toBeVisible();
    await expect(page.getByText('Robert C. Martin')).toBeVisible();
    await expect(page.getByText('ISBN: 9780132350884')).toBeVisible();
    await expect(page.getByText(/handbook of agile software/i)).toBeVisible();
    await expect(page.locator('.added-at')).toContainText('01 maart 2025');
  });

  test('displays book metadata for The Pragmatic Programmer', async ({ page }) => {
    await page.route('/api/books/2', route =>
      route.fulfill({ json: PRAGMATIC })
    );

    await page.goto('/books/2');

    await expect(page.getByRole('heading', { name: 'The Pragmatic Programmer' })).toBeVisible();
    await expect(page.getByText('David Thomas, Andrew Hunt')).toBeVisible();
    await expect(page.getByText('ISBN: 9780135957059')).toBeVisible();
    await expect(page.getByText(/journey to mastery/i)).toBeVisible();
  });

  test('renders the cover image when coverUrl is present', async ({ page }) => {
    await page.route('/api/books/1', route =>
      route.fulfill({ json: CLEAN_CODE })
    );

    await page.goto('/books/1');

    const cover = page.locator('.cover-side img.cover');
    await expect(cover).toBeVisible();
    await expect(cover).toHaveAttribute('alt', 'Clean Code');
  });

  test('renders cover placeholder when coverUrl is missing', async ({ page }) => {
    const bookWithoutCover = { ...CLEAN_CODE, coverUrl: null };
    await page.route('/api/books/1', route =>
      route.fulfill({ json: bookWithoutCover })
    );

    await page.goto('/books/1');

    await expect(page.locator('.cover-side .cover-placeholder')).toBeVisible();
    await expect(page.locator('.cover-side img')).toHaveCount(0);
  });

  test('shows "not found" state when the API returns 404', async ({ page }) => {
    await page.route('/api/books/999', route =>
      route.fulfill({ status: 404, json: {} })
    );

    await page.goto('/books/999');

    await expect(page.getByText('Boek niet gevonden.')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Terug naar bibliotheek' })).toBeVisible();
  });

  test('delete button shows a confirmation dialog', async ({ page }) => {
    await page.route('/api/books/1', route =>
      route.fulfill({ json: CLEAN_CODE })
    );

    await page.goto('/books/1');

    await expect(page.getByText('Weet je zeker')).toBeHidden();

    await page.getByRole('button', { name: 'Verwijderen uit bibliotheek' }).click();

    await expect(page.getByText('Weet je zeker dat je dit boek wil verwijderen?')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Ja, verwijderen' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Annuleren' })).toBeVisible();
  });

  test('cancelling delete hides the confirmation dialog', async ({ page }) => {
    await page.route('/api/books/1', route =>
      route.fulfill({ json: CLEAN_CODE })
    );

    await page.goto('/books/1');
    await page.getByRole('button', { name: 'Verwijderen uit bibliotheek' }).click();
    await page.getByRole('button', { name: 'Annuleren' }).click();

    await expect(page.getByText('Weet je zeker')).toBeHidden();
    await expect(page.getByRole('button', { name: 'Verwijderen uit bibliotheek' })).toBeVisible();
  });

  test('confirming delete calls DELETE API and navigates back to library', async ({ page }) => {
    await page.route('/api/books/1', route =>
      route.fulfill({ json: CLEAN_CODE })
    );

    let deleteWasCalled = false;
    await page.route('/api/books/1', route => {
      if (route.request().method() === 'DELETE') {
        deleteWasCalled = true;
        route.fulfill({ status: 204 });
      } else {
        route.fulfill({ json: CLEAN_CODE });
      }
    });

    await page.route('/api/books', route =>
      route.fulfill({ json: [] })
    );

    await page.goto('/books/1');
    await page.getByRole('button', { name: 'Verwijderen uit bibliotheek' }).click();
    await page.getByRole('button', { name: 'Ja, verwijderen' }).click();

    await expect(page).toHaveURL('/');
    expect(deleteWasCalled).toBe(true);
  });

  test('back link navigates to the library', async ({ page }) => {
    await page.route('/api/books/1', route =>
      route.fulfill({ json: CLEAN_CODE })
    );
    await page.route('/api/books', route =>
      route.fulfill({ json: BOOKS })
    );

    await page.goto('/books/1');
    await page.getByRole('link', { name: '← Terug naar bibliotheek' }).click();

    await expect(page).toHaveURL('/');
  });
});
