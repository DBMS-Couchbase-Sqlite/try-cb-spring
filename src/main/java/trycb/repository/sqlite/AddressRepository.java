package trycb.repository.sqlite;

import org.springframework.data.repository.CrudRepository;

import trycb.model.sqlite.Address;

public interface AddressRepository extends CrudRepository<Address, Long> {

}
