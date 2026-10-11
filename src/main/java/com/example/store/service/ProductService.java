package com.example.store.service;

import com.example.store.dto.ProductCreateRequest;
import com.example.store.dto.ProductDTO;
import com.example.store.entity.Product;
import com.example.store.mapper.ProductMapper;
import com.example.store.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    /** Every product with the IDs of its containing orders; the mapping happens inside the transaction. */
    @Transactional(readOnly = true)
    public List<ProductDTO> findAll() {
        return productMapper.productsToProductDTOs(productRepository.findAllWithOrders());
    }

    /** One product with the IDs of its containing orders, or {@link ResourceNotFoundException} (404). */
    @Transactional(readOnly = true)
    public ProductDTO findById(Long id) {
        return productRepository
                .findWithOrdersById(id)
                .map(productMapper::productToProductDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    /**
     * Creates a product from the request. The entity is built here so no client-supplied field other than
     * {@code description} can reach {@code save}. A new product is in no order, so the response carries an empty list.
     */
    @Transactional
    public ProductDTO create(ProductCreateRequest request) {
        Product product = new Product();
        product.setDescription(request.description());
        return productMapper.productToProductDTO(productRepository.save(product));
    }
}
