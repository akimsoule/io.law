# GitHub Copilot Instructions - io.law (Architecture Multi-Modules)

## Architecture du Projet

### Vue d'ensemble
Application Spring Batch modulaire pour extraire, traiter et consolider les lois et décrets du gouvernement béninois depuis https://sgg.gouv.bj/doc.

**Migration en cours** : Transformation du projet monolithique `law.spring` vers une architecture multi-modules `io.law`.

### Technologies
- **Java 17+** avec pattern matching, records, text blocks
- **Spring Boot 3.2.0** + Spring Batch
- **Maven Multi-Modules** (7 modules)
- **PDFBox** pour extraction PDF
- **Tesseract OCR** (via JavaCPP) pour OCR des PDFs scannés
- **MySQL 8.4** (Docker) pour persistance
- **Ollama** (optionnel) pour parsing IA en local
- **Groq API** (optionnel) pour parsing IA cloud (fallback)