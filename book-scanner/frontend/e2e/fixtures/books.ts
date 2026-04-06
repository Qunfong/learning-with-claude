import { Book } from '../../src/app/models/book.model';

export const BOOKS: Book[] = [
  {
    id: 1,
    title: 'Clean Code',
    author: 'Robert C. Martin',
    isbn: '9780132350884',
    coverUrl: 'https://covers.openlibrary.org/b/isbn/9780132350884-L.jpg',
    description:
      'A handbook of agile software craftsmanship. Even bad code can function, but if it is not clean, it can bring a development organization to its knees.',
    addedAt: '2025-03-01T10:00:00Z',
  },
  {
    id: 2,
    title: 'The Pragmatic Programmer',
    author: 'David Thomas, Andrew Hunt',
    isbn: '9780135957059',
    coverUrl: 'https://covers.openlibrary.org/b/isbn/9780135957059-L.jpg',
    description:
      'Your journey to mastery. Cuts through the increasing complexity of software development to examine the core process — taking a requirement and producing working, maintainable code.',
    addedAt: '2025-03-15T14:30:00Z',
  },
  {
    id: 3,
    title: 'Designing Data-Intensive Applications',
    author: 'Martin Kleppmann',
    isbn: '9781449373320',
    coverUrl: 'https://covers.openlibrary.org/b/isbn/9781449373320-L.jpg',
    description:
      'The big ideas behind reliable, scalable, and maintainable systems. Explores the pros and cons of various technologies for processing and storing data.',
    addedAt: '2025-04-01T09:15:00Z',
  },
];
