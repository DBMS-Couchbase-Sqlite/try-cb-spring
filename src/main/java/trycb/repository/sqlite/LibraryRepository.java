package trycb.repository.sqlite;

import org.springframework.data.repository.CrudRepository;

import trycb.model.sqlite.Library;

public interface LibraryRepository extends CrudRepository<Library, Long> {

}
