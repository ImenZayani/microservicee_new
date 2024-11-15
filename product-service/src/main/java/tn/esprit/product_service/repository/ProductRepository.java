package tn.esprit.product_service.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import tn.esprit.product_service.model.Product;

public interface ProductRepository extends MongoRepository<Product, String> {
}
