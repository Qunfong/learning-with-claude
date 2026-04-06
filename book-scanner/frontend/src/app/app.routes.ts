import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/library/library.component').then(m => m.LibraryComponent)
  },
  {
    path: 'scan',
    loadComponent: () =>
      import('./pages/scan/scan.component').then(m => m.ScanComponent)
  },
  {
    path: 'books/:id',
    loadComponent: () =>
      import('./pages/book-detail/book-detail.component').then(m => m.BookDetailComponent)
  },
  { path: '**', redirectTo: '' }
];
