package trycb.repository.sqlite;

import org.springframework.data.repository.CrudRepository;

import trycb.model.sqlite.Author;

public interface AuthorRepository extends CrudRepository<Author, Long> {

}
