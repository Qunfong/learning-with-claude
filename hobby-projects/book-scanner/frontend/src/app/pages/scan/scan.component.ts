import { Component, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { BookService } from '../../services/book.service';
import { BookCandidate, BookRecognizedEvent, SaveBookRequest } from '../../models/book.model';

type ScanState = 'idle' | 'uploading' | 'processing' | 'recognized' | 'failed';

@Component({
  selector: 'app-scan',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './scan.component.html',
  styleUrl: './scan.component.scss'
})
export class ScanComponent {

  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;
  @ViewChild('videoEl') videoEl!: ElementRef<HTMLVideoElement>;

  state: ScanState = 'idle';
  errorMessage = '';
  candidates: BookCandidate[] = [];
  private sseConnection: EventSource | null = null;
  private mediaStream: MediaStream | null = null;
  isCameraActive = false;

  constructor(
    private bookService: BookService,
    private router: Router
  ) {}

  // ─── Camera ────────────────────────────────────────────────────────────────

  async openCamera(): Promise<void> {
    try {
      this.mediaStream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' }  // achtercamera op mobiel
      });
      this.videoEl.nativeElement.srcObject = this.mediaStream;
      this.isCameraActive = true;
    } catch (err) {
      this.showError('Camera kon niet worden geopend. Controleer de browserpermissies.');
    }
  }

  capturePhoto(): void {
    const video = this.videoEl.nativeElement;
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    canvas.getContext('2d')!.drawImage(video, 0, 0);

    canvas.toBlob(blob => {
      if (blob) {
        const file = new File([blob], 'capture.jpg', { type: 'image/jpeg' });
        this.stopCamera();
        this.processFile(file);
      }
    }, 'image/jpeg', 0.9);
  }

  stopCamera(): void {
    this.mediaStream?.getTracks().forEach(t => t.stop());
    this.mediaStream = null;
    this.isCameraActive = false;
  }

  // ─── File upload ───────────────────────────────────────────────────────────

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      this.processFile(input.files[0]);
    }
  }

  private async processFile(file: File): Promise<void> {
    this.state = 'uploading';
    this.errorMessage = '';

    try {
      const { uploadId } = await this.bookService.uploadImage(file);
      this.state = 'processing';
      this.connectSse(uploadId);
    } catch (err: any) {
      const msg = err?.response?.data?.error ?? 'Upload mislukt';
      this.showError(msg);
    }
  }

  // ─── SSE ───────────────────────────────────────────────────────────────────

  private connectSse(uploadId: string): void {
    this.sseConnection = new EventSource(`/api/sse/status/${uploadId}`);

    this.sseConnection.addEventListener('book-status', (event: MessageEvent) => {
      const data: BookRecognizedEvent = JSON.parse(event.data);
      this.handleSseEvent(data);
    });

    this.sseConnection.onerror = () => {
      this.showError('Verbinding met server verbroken.');
    };
  }

  private handleSseEvent(event: BookRecognizedEvent): void {
    if (event.status === 'RECOGNIZED') {
      this.candidates = event.candidates ?? [];
      this.state = 'recognized';
      this.sseConnection?.close();
    } else if (event.status === 'FAILED') {
      const messages: Record<string, string> = {
        NO_TEXT_DETECTED: 'Geen tekst gevonden op de afbeelding. Probeer een scherpere foto.',
        OCR_SERVICE_ERROR: 'De tekst-herkenning service is niet beschikbaar.',
        BOOK_NOT_FOUND: 'Kon geen boek vinden op basis van de afbeelding.'
      };
      this.showError(messages[event.errorCode ?? ''] ?? 'Onbekende fout');
      this.sseConnection?.close();
    }
  }

  // ─── Opslaan ───────────────────────────────────────────────────────────────

  async saveCandidate(candidate: BookCandidate): Promise<void> {
    const request: SaveBookRequest = {
      title: candidate.title,
      author: candidate.author,
      isbn: candidate.isbn,
      coverUrl: candidate.coverUrl,
      description: candidate.description
    };
    await this.bookService.saveBook(request);
    this.router.navigate(['/']);
  }

  dismiss(): void {
    this.reset();
  }

  retry(): void {
    this.reset();
  }

  // ─── Helpers ───────────────────────────────────────────────────────────────

  private showError(msg: string): void {
    this.errorMessage = msg;
    this.state = 'failed';
  }

  private reset(): void {
    this.state = 'idle';
    this.errorMessage = '';
    this.candidates = [];
    this.sseConnection?.close();
    this.sseConnection = null;
    if (this.fileInput) {
      this.fileInput.nativeElement.value = '';
    }
  }
}
