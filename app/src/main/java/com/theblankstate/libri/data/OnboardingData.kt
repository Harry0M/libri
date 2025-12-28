package com.theblankstate.libri.data

object OnboardingData {
    val languages = listOf(
        "Hindi", "English", "Urdu", "Spanish", "Chinese", "French", "Arabic", "Portuguese"
    )

    val authorsByLanguage = mapOf(
        "Hindi" to listOf(
            "Munshi Premchand", "Harivansh Rai Bachchan", "Mahadevi Verma", "Jaishankar Prasad",
            "Suryakant Tripathi ‘Nirala’", "Ramdhari Singh ‘Dinkar’", "Bhisham Sahni",
            "Nirmal Verma", "Mannu Bhandari", "Gajanan Madhav Muktibodh"
        ),
        "Urdu" to listOf(
            "Mirza Ghalib", "Faiz Ahmed Faiz", "Saadat Hasan Manto", "Ismat Chughtai",
            "Jigar Moradabadi", "Ahmed Faraz", "Mir Taqi Mir", "Parveen Shakir",
            "Krishan Chander", "Ibne Insha"
        ),
        "Spanish" to listOf(
            "Miguel de Cervantes", "Gabriel García Márquez", "Pablo Neruda", "Jorge Luis Borges",
            "Isabel Allende", "Mario Vargas Llosa", "Federico García Lorca", "Carlos Ruiz Zafón",
            "Julio Cortázar", "Camilo José Cela"
        ),
        "Chinese" to listOf(
            "Lu Xun", "Cao Xueqin", "Confucius", "Wu Cheng’en", "Lao She", "Mo Yan",
            "Li Bai", "Sun Tzu", "Bai Juyi", "Wang Wei"
        ),
        "French" to listOf(
            "Victor Hugo", "Alexandre Dumas", "Albert Camus", "Marcel Proust", "Voltaire",
            "Molière", "Jules Verne", "Jean-Paul Sartre", "Gustave Flaubert", "Antoine de Saint-Exupéry"
        ),
        "Arabic" to listOf(
            "Naguib Mahfouz", "Khalil Gibran", "Al-Mutanabbi", "Taha Hussein", "Ibn Khaldun",
            "Adonis (Ali Ahmad Said)", "Mahmoud Darwish", "Ibn Arabi", "Nizar Qabbani", "Al-Jahiz"
        ),
        "Portuguese" to listOf(
            "José Saramago", "Fernando Pessoa", "Paulo Coelho", "Machado de Assis", "Luís de Camões",
            "Clarice Lispector", "Jorge Amado", "Eça de Queirós", "Gonçalo M. Tavares", "Mia Couto"
        ),
        "English" to listOf(
            "William Shakespeare", "Charles Dickens", "Jane Austen", "Mark Twain", "George Orwell",
            "J.K. Rowling", "J.R.R. Tolkien", "Ernest Hemingway", "Agatha Christie", "Stephen King"
        )
    )

    val genres = listOf(
        "Fiction", "Mystery", "Thriller", "Science Fiction", "Fantasy", "Romance", "Historical Fiction",
        "Horror", "Biography", "Autobiography", "Memoir", "Self-Help", "Business", "Travel", "History",
        "Science", "Philosophy", "Psychology", "Poetry", "Comics", "Art", "Cooking", "Health", "Children's"
    )
}
