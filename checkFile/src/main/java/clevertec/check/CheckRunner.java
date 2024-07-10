package clevertec.check;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Product {
    private int id;
    private String description;
    private BigDecimal price;
    private int quantityInStock;
    private boolean wholesaleProduct;

    public Product(int id, String description, double price, int quantityInStock, boolean wholesaleProduct) {
        this.id = id;
        this.description = description;
        this.price = BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP);
        this.quantityInStock = quantityInStock;
        this.wholesaleProduct = wholesaleProduct;
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getQuantityInStock() {
        return quantityInStock;
    }

    public boolean isWholesaleProduct() {
        return wholesaleProduct;
    }
}

class DiscountCard {
    private static final BigDecimal DEFAULT_DISCOUNT_RATE = BigDecimal.valueOf(2.00).setScale(2, RoundingMode.HALF_UP);

    private int number;
    private BigDecimal discountRate;

    public DiscountCard(int number, double discountRate) {
        this.number = number;
        this.discountRate = BigDecimal.valueOf(discountRate).setScale(2, RoundingMode.HALF_UP);
    }

    public int getNumber() {
        return number;
    }

    public BigDecimal getDiscountRate() {
        return discountRate;
    }

    public static BigDecimal getDefaultDiscountRate() {
        return DEFAULT_DISCOUNT_RATE;
    }
}

public class CheckRunner {
    private List<Product> products;
    private List<DiscountCard> discountCards;

    public CheckRunner(String productFilePath, String discountCardFilePath) throws IOException {
        products = new ArrayList<>();
        discountCards = new ArrayList<>();

        try (Reader reader1 = new FileReader(productFilePath)) {
            CSVFormat csvFormat1 = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withDelimiter(';')
                    .withTrim();
            CSVParser csvParser1 = new CSVParser(reader1, csvFormat1);
            for (CSVRecord record : csvParser1) {
                Product product = new Product(
                        Integer.parseInt(record.get("id")),
                        record.get("description"),
                        Double.parseDouble(record.get("price")),
                        Integer.parseInt(record.get("quantity_in_stock")),
                        Boolean.parseBoolean(record.get("wholesale_product"))
                );
                products.add(product);
            }
        }

        try (Reader reader2 = new FileReader(discountCardFilePath)) {
            CSVFormat csvFormat2 = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withDelimiter(';')
                    .withTrim();
            CSVParser csvParser2 = new CSVParser(reader2, csvFormat2);
            for (CSVRecord record : csvParser2) {
                DiscountCard card = new DiscountCard(
                        Integer.parseInt(record.get("number")),
                        Double.parseDouble(record.get("amount"))
                );
                discountCards.add(card);
            }
        }
    }

    public List<Product> queryProducts(Map<Integer, Integer> productQuantities) throws IllegalArgumentException {
        List<Product> result = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : productQuantities.entrySet()) {
            int productId = entry.getKey();
            int quantity = entry.getValue();
            boolean found = false;
            for (Product product : products) {
                if (product.getId() == productId) {
                    found = true;
                    for (int i = 0; i < quantity; i++) {
                        result.add(product);
                    }
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Product with ID " + productId + " not found.");
            }
        }
        return result;
    }

    public DiscountCard queryDiscountCard(int cardNumber) {
        for (DiscountCard card : discountCards) {
            if (card.getNumber() == cardNumber) {
                return card;
            }
        }
        // If card is not found, return default discount card with 2% discount
        return new DiscountCard(cardNumber, DiscountCard.getDefaultDiscountRate().doubleValue());
    }

    public void generateReceipt(List<Product> products, DiscountCard discountCard, Double balanceDebitCard, String resultFilePath) throws IOException {
        BigDecimal totalSumWithoutDiscounts = BigDecimal.ZERO;
        BigDecimal totalSumWithDiscounts = BigDecimal.ZERO;
        Map<Product, Integer> productCounts = new HashMap<>();

        for (Product product : products) {
            productCounts.put(product, productCounts.getOrDefault(product, 0) + 1);
        }

        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String time = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        System.out.println("Date: " + date);
        System.out.println("Time: " + time);
        System.out.println();
        System.out.printf("%-5s %-30s %-10s %-10s %-10s %-15s\n", "QTY", "DESCRIPTION", "PRICE", "TOTAL", "DISCOUNT", "DISCOUNT INFO");

        try (CSVPrinter printer = new CSVPrinter(new FileWriter(resultFilePath), CSVFormat.DEFAULT.withDelimiter(';'))) {
            printer.printRecord("Date", date);
            printer.printRecord("Time", time);
            printer.printRecord("QTY", "DESCRIPTION", "PRICE", "DISCOUNT", "TOTAL");

            for (Map.Entry<Product, Integer> entry : productCounts.entrySet()) {
                Product product = entry.getKey();
                int quantity = entry.getValue();
                BigDecimal productTotalPrice = product.getPrice().multiply(BigDecimal.valueOf(quantity));
                BigDecimal discount = BigDecimal.ZERO;
                String discountInfo = "";

                totalSumWithoutDiscounts = totalSumWithoutDiscounts.add(productTotalPrice);

                BigDecimal productTotalPriceWithDiscount = productTotalPrice;

                if (product.isWholesaleProduct() && quantity >= 5) {
                    discount = productTotalPrice.multiply(BigDecimal.valueOf(0.10));
                    discountInfo = "10% wholesale";
                    productTotalPriceWithDiscount = productTotalPrice.subtract(discount).setScale(2, RoundingMode.HALF_UP);
                }

                if (!product.isWholesaleProduct() || quantity < 5) {
                    if (discountCard != null && discountCard.getDiscountRate().compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal cardDiscount = productTotalPriceWithDiscount.multiply(discountCard.getDiscountRate().divide(BigDecimal.valueOf(100))).setScale(2, RoundingMode.HALF_UP);
                        productTotalPriceWithDiscount = productTotalPriceWithDiscount.subtract(cardDiscount).setScale(2, RoundingMode.HALF_UP);
                        discount = discount.add(cardDiscount);
                        discountInfo = discountCard.getDiscountRate() + "% card discount";
                    }
                }

                totalSumWithDiscounts = totalSumWithDiscounts.add(productTotalPriceWithDiscount);

                // Print to console
                System.out.printf("%-5d %-30s $%-9.2f $%-9.2f $%-9.2f %-15s\n",
                        quantity,
                        product.getDescription(),
                        product.getPrice(),
                        productTotalPrice.setScale(2, RoundingMode.HALF_UP),
                        discount.setScale(2, RoundingMode.HALF_UP),
                        discountInfo);

                // Print to CSV file
                printer.printRecord(
                        quantity,
                        product.getDescription(),
                        product.getPrice() + "$",
                        discount.setScale(2, RoundingMode.HALF_UP) + "$",
                        productTotalPriceWithDiscount.setScale(2, RoundingMode.HALF_UP) + "$"
                );
            }

            totalSumWithoutDiscounts = totalSumWithoutDiscounts.setScale(2, RoundingMode.HALF_UP);
            totalSumWithDiscounts = totalSumWithDiscounts.setScale(2, RoundingMode.HALF_UP);

            // Print totals to console
            System.out.println();
            System.out.printf("TOTAL PRICE: $%-10.2f\n", totalSumWithoutDiscounts);
            System.out.printf("TOTAL DISCOUNT: $%-10.2f\n", totalSumWithoutDiscounts.subtract(totalSumWithDiscounts));
            System.out.printf("TOTAL WITH DISCOUNT: $%-10.2f\n", totalSumWithDiscounts);

            // Print totals to CSV file
            printer.printRecord("DISCOUNT CARD", "DISCOUNT PERCENTAGE");
            if (discountCard != null) {
                printer.printRecord(discountCard.getNumber(), discountCard.getDiscountRate() + "%");
            }
            printer.printRecord();
            printer.printRecord(
                    "TOTAL PRICE",
                    "TOTAL DISCOUNT",
                    "TOTAL WITH DISCOUNT"
            );
            printer.printRecord(
                    totalSumWithoutDiscounts + "$",
                    totalSumWithoutDiscounts.subtract(totalSumWithDiscounts) + "$",
                    totalSumWithDiscounts + "$"
            );

            // Print balance debit card to CSV file if present
            if (balanceDebitCard != null) {
                BigDecimal balanceDebitCardBD = BigDecimal.valueOf(balanceDebitCard).setScale(2, RoundingMode.HALF_UP);
                printer.printRecord("BALANCE DEBIT CARD", balanceDebitCardBD + "$");
                printer.printRecord("TOTAL BALANCE AFTER PURCHASE", balanceDebitCardBD.subtract(totalSumWithDiscounts) + "$");
            }
        }
    }

    private static void saveErrorMessage(String errorMessage, String saveToFile) {
        try (FileWriter writer = new FileWriter(saveToFile)) {
            writer.write("Error: " + errorMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Map<Integer, Integer> productQuantities = new HashMap<>();
        DiscountCard discountCard = null;
        Double balanceDebitCard = null;
        String pathToFile = null;
        String saveToFile = null;

        try {
            for (String arg : args) {
                if (arg.startsWith("discountCard=")) {
                    String[] parts = arg.split("=");
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        discountCard = new DiscountCard(Integer.parseInt(parts[1]), 0);
                    }
                } else if (arg.startsWith("balanceDebitCard=")) {
                    String[] parts = arg.split("=");
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        balanceDebitCard = Double.valueOf(parts[1]);
                    }
                } else if (arg.startsWith("pathToFile=")) {
                    String[] parts = arg.split("=");
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        pathToFile = parts[1];
                    }
                } else if (arg.startsWith("saveToFile=")) {
                    String[] parts = arg.split("=");
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        saveToFile = parts[1];
                    }
                } else {
                    String[] idAndQuantity = arg.split("-");
                    if (idAndQuantity.length > 1) {
                        int productId = Integer.parseInt(idAndQuantity[0]);
                        int productQuantity = Integer.parseInt(idAndQuantity[1]);
                        productQuantities.put(productId, productQuantity);
                    }
                }
            }

            if (pathToFile == null || saveToFile == null) {
                throw new IllegalArgumentException("Path to file and save to file must be specified.");
            }

            CheckRunner checkRunner = new CheckRunner(pathToFile, "./src/main/resources/discountCards.csv");
            List<Product> products = checkRunner.queryProducts(productQuantities);
            DiscountCard finalDiscountCard = discountCard != null ? checkRunner.queryDiscountCard(discountCard.getNumber()) : null;
            checkRunner.generateReceipt(products, finalDiscountCard, balanceDebitCard, saveToFile);

        } catch (Exception e) {
            if (saveToFile != null) {
                saveErrorMessage(e.getMessage(), saveToFile);
            } else {
                e.printStackTrace();
            }
        }
    }
}