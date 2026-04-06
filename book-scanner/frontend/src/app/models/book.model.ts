export interface BookCandidate {
  title: string;
  author: string | null;
  isbn: string | null;
  coverUrl: string | null;
  description: string | null;
}

export interface BookRecognizedEvent {
  uploadId: string;
  status: 'PROCESSING' | 'RECOGNIZED' | 'FAILED';
  errorCode?: 'NO_TEXT_DETECTED' | 'OCR_SERVICE_ERROR' | 'BOOK_NOT_FOUND';
  candidates?: BookCandidate[];
}

export interface Book {
  id: number;
  title: string;
  author: string | null;
  isbn: string | null;
  coverUrl: string | null;
  description: string | null;
  addedAt: string;
}

export interface SaveBookRequest {
  title: string;
  author: string | null;
  isbn: string | null;
  coverUrl: string | null;
  description: string | null;
}
