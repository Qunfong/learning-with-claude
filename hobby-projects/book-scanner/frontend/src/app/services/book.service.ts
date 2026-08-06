import { Injectable } from '@angular/core';
import axios from 'axios';
import { Book, SaveBookRequest } from '../models/book.model';

const API_BASE = '/api';

@Injectable({ providedIn: 'root' })
export class BookService {

  async uploadImage(file: File): Promise<{ uploadId: string }> {
    const formData = new FormData();
    formData.append('file', file);
    const response = await axios.post<{ uploadId: string }>(
      `${API_BASE}/images/upload`,
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    );
    return response.data;
  }

  async getAllBooks(): Promise<Book[]> {
    const response = await axios.get<Book[]>(`${API_BASE}/books`);
    return response.data;
  }

  async getBook(id: number): Promise<Book> {
    const response = await axios.get<Book>(`${API_BASE}/books/${id}`);
    return response.data;
  }

  async saveBook(request: SaveBookRequest): Promise<Book> {
    const response = await axios.post<Book>(`${API_BASE}/books`, request);
    return response.data;
  }

  async deleteBook(id: number): Promise<void> {
    await axios.delete(`${API_BASE}/books/${id}`);
  }
}
