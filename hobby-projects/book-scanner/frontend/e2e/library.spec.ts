import { test, expect } from '@playwright/test';
import { BOOKS } from './fixtures/books';

test.describe('Library page', () => {
  test('shows empty state when the library contains no books', async ({ page }) => {
    await page.route('/api/books', route =>
      route.fulfill({ json: [] })
    );

    await page.goto('/');

    await expect(page.getByText('Je bibliotheek is nog leeg.')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Scan je eerste boek' })).toBeVisible();
  });

  test('renders all books in a grid', async ({ page }) => {
    await page.route('/api/books', route =>
      route.fulfill({ json: BOOKS })
    );

    await page.goto('/');

    for (const book of BOOKS) {
      await expect(page.getByRole('heading', { name: book.title })).toBeVisible();
      await expect(page.getByText(book.author!)).toBeVisible();
    }
  });

  test('shows cover images when a coverUrl is present', async ({ page }) => {
    await page.route('/api/books', route =>
      route.fulfill({ json: BOOKS })
    );

    await page.goto('/');

    const covers = page.locator('.book-card img.cover');
    await expect(covers).toHaveCount(BOOKS.filter(b => b.coverUrl).length);
  });

  test('shows cover placeholder when coverUrl is missing', async ({ page }) => {
    const bookWithoutCover = { ...BOOKS[0], coverUrl: null };
    await page.route('/api/books', route =>
      route.fulfill({ json: [bookWithoutCover] })
    );

    await page.goto('/');

    await expect(page.locator('.cover-placeholder')).toBeVisible();
    await expect(page.locator('.book-card img.cover')).toHaveCount(0);
  });

  test('navigates to book detail when a book card is clicked', async ({ page }) => {
    await page.route('/api/books', route =>
      route.fulfill({ json: BOOKS })
    );
    await page.route('/api/books/1', route =>
      route.fulfill({ json: BOOKS[0] })
    );

    await page.goto('/');
    await page.getByRole('heading', { name: 'Clean Code' }).click();

    await expect(page).toHaveURL('/books/1');
  });

  test('has a working link to the scan page', async ({ page }) => {
    await page.route('/api/books', route =>
      route.fulfill({ json: [] })
    );

    await page.goto('/');
    await page.getByRole('link', { name: '+ Boek scannen' }).click();

    await expect(page).toHaveURL('/scan');
  });
});
