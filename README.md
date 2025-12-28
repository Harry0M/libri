# Libri 📚

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="120" alt="Libri Logo">
</p>

<p align="center">
  <strong>Your Personal Book Discovery & Reading Companion</strong>
</p>

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.theblankstate.libri">
    <img src="https://img.shields.io/badge/Google%20Play-Download-green?style=for-the-badge&logo=google-play" alt="Get it on Google Play">
  </a>
</p>

---

## 📖 About

**Libri** is a modern Android app that helps you discover, read, and manage your book collection. With access to millions of books from Open Library and thousands of free eBooks from Project Gutenberg, Libri puts the world's literature at your fingertips.

## ✨ Features

### 📚 Discover Books
- **Open Library Integration**: Access millions of books from the world's largest open library
- **Project Gutenberg**: Browse and download 70,000+ free eBooks
- **Smart Search**: Search by title, author, subject, or ISBN
- **Personalized Recommendations**: Get book suggestions based on your reading preferences

### 📱 Reading Experience
- **Built-in Reader**: Read books directly in the app
- **Multiple Formats**: Support for EPUB, PDF, and more
- **Offline Reading**: Download books for offline access
- **Reading Progress**: Track your reading progress across devices

### 👤 Personal Library
- **Custom Bookshelves**: Organize books into custom collections
- **Reading Lists**: Create and manage your reading lists
- **Favorites**: Quick access to your favorite books
- **Reading History**: Keep track of what you've read

### 🔗 Integrations
- **Open Library Account**: Connect your Open Library account to borrow books
- **Google Sign-In**: Secure authentication with your Google account
- **Cloud Sync**: Sync your preferences across devices

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Architecture**: MVVM (Model-View-ViewModel)
- **Networking**: Retrofit + OkHttp
- **Image Loading**: Coil
- **Authentication**: Firebase Auth + Google Sign-In
- **Database**: Firebase Realtime Database
- **Navigation**: Jetpack Navigation Compose

## 📱 Requirements

- Android 7.0 (API 24) or higher
- Internet connection for book discovery and downloads

## 🏗️ Building the Project

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 11 or higher
- Android SDK 35

### Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/Harry0M/Libri.git
   cd Libri
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned directory

3. **Configure Firebase**
   - Create a Firebase project at [Firebase Console](https://console.firebase.google.com/)
   - Add an Android app with package name `com.theblankstate.libri`
   - Download `google-services.json` and place it in the `app/` directory

4. **Build and Run**
   ```bash
   ./gradlew assembleDebug
   ```
   Or use Android Studio's Run button

## 📄 Documentation

- [Privacy Policy](PRIVACY_POLICY.md)
- [Terms of Service](TERMS_OF_SERVICE.md)

## 🤝 Contributing

We welcome contributions! Please feel free to submit issues and pull requests.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📧 Contact

**The Blank State Team**
- Email: [theblankstateteam@gmail.com](mailto:theblankstateteam@gmail.com)
- GitHub: [@Harry0M](https://github.com/Harry0M)

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- [Open Library](https://openlibrary.org/) - For their amazing open book API
- [Project Gutenberg](https://www.gutenberg.org/) - For providing free eBooks
- [Gutendex](https://gutendex.com/) - For the Gutenberg API
- [Material Design 3](https://m3.material.io/) - For the beautiful design system

---

<p align="center">
  Made with ❤️ by The Blank State Team
</p>
