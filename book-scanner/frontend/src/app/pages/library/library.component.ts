import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { BookService } from '../../services/book.service';
import { Book } from '../../models/book.model';

@Component({
  selector: 'app-library',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './library.component.html',
  styleUrl: './library.component.scss'
})
export class LibraryComponent implements OnInit {
  books: Book[] = [];
  loading = true;

  constructor(private bookService: BookService) {}

  async ngOnInit(): Promise<void> {
    this.books = await this.bookService.getAllBooks();
    this.loading = false;
  }
}
