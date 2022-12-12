package trycb.repository.sqlite;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import trycb.model.sqlite.Book;
import trycb.projections.sqlite.CustomBook;

@RepositoryRestResource(excerptProjection = CustomBook.class)
public interface BookRepository extends CrudRepository<Book, Long> {
}
