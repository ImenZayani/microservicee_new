package tn.esprit.order_service.service;

import brave.Span;
import brave.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import tn.esprit.order_service.dto.InventoryResponse;
import tn.esprit.order_service.dto.OrderLineItemsDto;
import tn.esprit.order_service.dto.OrderRequest;
import tn.esprit.order_service.event.OrderPlacedEvent;
import tn.esprit.order_service.model.Order;
import tn.esprit.order_service.model.OrderLineItems;
import tn.esprit.order_service.repository.OrderRepository;
import org.springframework.kafka.core.KafkaTemplate;


import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

	private final OrderRepository orderRepository;
	private final WebClient.Builder webClientBuilder;
	private final KafkaTemplate<String, OrderPlacedEvent> kafkaTemplate;
	private final Tracer tracer;

	public String placeOrder(OrderRequest orderRequest) {
		Order order = new Order();
		order.setOrderNumber(UUID.randomUUID().toString());

		List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
				.stream()
				.map(this::mapToDto)
				.toList();

		order.setOrderLineItemsList(orderLineItems);

		List<String> skuCodes = order.getOrderLineItemsList().stream()
				.map(OrderLineItems::getSkuCode)
				.toList();

		Span inventoryServiceLookup = tracer.nextSpan().name("InventoryServiceLookup");

		try (Tracer.SpanInScope isLookup = tracer.withSpanInScope(inventoryServiceLookup.start())) {

			inventoryServiceLookup.tag("call", "inventory-service");
			// Call Inventory Service, and place order if product is in
			// stock
			InventoryResponse[] inventoryResponsArray = webClientBuilder.build().get()
					.uri("http://inventory-service/api/inventory",
							uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
					.retrieve()
					.bodyToMono(InventoryResponse[].class)
					.block();

			boolean allProductsInStock = Arrays.stream(inventoryResponsArray)
					.allMatch(InventoryResponse::isInStock);

			if (allProductsInStock) {
				orderRepository.save(order);
				kafkaTemplate.send("notificationTopic", new OrderPlacedEvent(order.getOrderNumber()));
				return "Order Placed Successfully";
			} else {
				throw new IllegalArgumentException("Product is not in stock, please try again later");
			}
		} finally {
			inventoryServiceLookup.flush();
		}
	}

	private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
		OrderLineItems orderLineItems = new OrderLineItems();
		orderLineItems.setPrice(orderLineItemsDto.getPrice());
		orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
		orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
		return orderLineItems;
	}
}