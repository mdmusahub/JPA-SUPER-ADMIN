package com.example.bookstore;

import com.example.bookstore.domain.Author;
import com.example.bookstore.domain.Book;
import com.example.bookstore.domain.Publisher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds a few rows so the console has counts to show: 2 publishers, 3 authors, 5 books. */
@Component
class BookstoreData implements ApplicationRunner {

    static final int PUBLISHERS = 2;
    static final int AUTHORS = 3;
    static final int BOOKS = 5;

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (em.createQuery("select count(b) from Book b", Long.class).getSingleResult() > 0) {
            return;
        }
        Publisher[] publishers = new Publisher[PUBLISHERS];
        for (int i = 0; i < PUBLISHERS; i++) {
            publishers[i] = new Publisher("Publisher " + (i + 1));
            em.persist(publishers[i]);
        }
        Author[] authors = new Author[AUTHORS];
        for (int i = 0; i < AUTHORS; i++) {
            authors[i] = new Author("Author " + (i + 1));
            em.persist(authors[i]);
        }
        for (int i = 0; i < BOOKS; i++) {
            Book book = new Book("Book " + (i + 1), authors[i % AUTHORS], publishers[i % PUBLISHERS]);
            book.setSecretToken("token-" + i + "-not-a-real-secret");
            em.persist(book);
        }
    }
}
