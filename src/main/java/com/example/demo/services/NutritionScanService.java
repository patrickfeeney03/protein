package com.example.demo.services;

import com.example.demo.NutritionScannerProperties;
import com.example.demo.MultipartImageNormalizer;
import com.example.demo.entities.FoodEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
public class NutritionScanService {
    private static final Logger logger = LoggerFactory.getLogger(NutritionScanService.class);

    private final OcrApiClient ocrApiClient;
    private final ObjectMapper objectMapper;
    private final NutritionScannerProperties properties;
    private final NutritionTableParser tableParser;
    private final NutritionMarkdownParser nutritionMarkdownParser;
    private final ProductTextParser productTextParser;
    private final AnnotationParser annotationParser;
    private final ScanResultMerger scanResultMerger;

    @Autowired
    public NutritionScanService(
            ObjectMapper objectMapper,
            NutritionScannerProperties properties
    ) {
        this.ocrApiClient = new OcrApiClient(properties);
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.tableParser = new NutritionTableParser();
        this.nutritionMarkdownParser = new NutritionMarkdownParser(tableParser);
        this.productTextParser = new ProductTextParser();
        this.annotationParser = new AnnotationParser(objectMapper);
        this.scanResultMerger = new ScanResultMerger();
    }

    NutritionScanService(
            ObjectMapper objectMapper,
            NutritionScannerProperties properties,
            OcrApiClient ocrApiClient
    ) {
        this.ocrApiClient = ocrApiClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.tableParser = new NutritionTableParser();
        this.nutritionMarkdownParser = new NutritionMarkdownParser(tableParser);
        this.productTextParser = new ProductTextParser();
        this.annotationParser = new AnnotationParser(objectMapper);
        this.scanResultMerger = new ScanResultMerger();
    }

    public ScanResult scan(List<MultipartFile> images) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            logger.error("Failed to scan. No Mistral OCR api key.");
            return ScanResult.failed(List.of("Mistral OCR is not configured"));
        }

        var normalizedImages = normalizeFiles(images);
        if (normalizedImages.isEmpty()) {
            return ScanResult.failed(List.of("At least one image is required"));
        }

        var startedAtNanos = System.nanoTime();

        var contentTypeFailure = validateImageContentTypes(normalizedImages);
        if (contentTypeFailure != null) {
            return contentTypeFailure;
        }

        var asyncResult = processImagesAsync(normalizedImages);

        logger.info(
                "Nutrition scan completed imageCount={} requestMs={} parseMs={} totalMs={} nutritionSource={} productSource={} annotationFallback={} productAnnotationFallback={} totalWeight={} servingsPerContainer100={}",
                normalizedImages.size(),
                asyncResult.totalRequestMs(),
                asyncResult.totalParseMs(),
                elapsedMillis(startedAtNanos),
                asyncResult.result().sourceUsed(),
                asyncResult.result().productSourceUsed(),
                asyncResult.result().usedAnnotationFallback(),
                asyncResult.result().productUsedAnnotationFallback(),
                asyncResult.result().product().totalWeight(),
                asyncResult.result().product().getServingsPerContainer100()
        );
        return asyncResult.result();
    }

    private ScanResult validateImageContentTypes(List<MultipartFile> images) {
        if (images.size() > properties.getMaxImages()) {
            return ScanResult.failed(List.of("A maximum of " + properties.getMaxImages() + " images is supported"));
        }
        for (var image : images) {
            var contentType = image.getContentType();
            if (image.getSize() > properties.getMaxImageBytes()) {
                return ScanResult.failed(List.of("Image exceeds the maximum allowed size"));
            }
            if (contentType == null || contentType.isBlank()
                    || !Set.of("image/jpeg", "image/png", "image/webp").contains(contentType.toLowerCase(Locale.ROOT))) {
                logger.warn("Rejected unsupported image upload content type");
                return ScanResult.failed(List.of("Only image uploads are supported"));
            }
            if (properties.isValidateImageSignature() && !hasAllowedImageSignature(image)) {
                return ScanResult.failed(List.of("Image content does not match its declared type"));
            }
        }
        return null;
    }

    private boolean hasAllowedImageSignature(MultipartFile image) {
        try (InputStream input = image.getInputStream()) {
            var header = input.readNBytes(12);
            var png = header.length >= 8
                    && (header[0] & 0xff) == 0x89 && header[1] == 0x50 && header[2] == 0x4e
                    && header[3] == 0x47 && header[4] == 0x0d && header[5] == 0x0a
                    && header[6] == 0x1a && header[7] == 0x0a;
            var jpeg = header.length >= 3
                    && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                    && (header[2] & 0xff) == 0xff;
            var webp = header.length >= 12
                    && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            return png || jpeg || webp;
        } catch (IOException e) {
            return false;
        }
    }

    private List<MultipartFile> normalizeFiles(List<MultipartFile> files) {
        return MultipartImageNormalizer.normalize(files);
    }

    private SingleImageScanResult requestAndParseSingleImage(MultipartFile image) throws IOException {
        var requestStartedAtNanos = System.nanoTime();
        var responseBody = ocrApiClient.ocrRequest(image);
        var requestElapsedMs = elapsedMillis(requestStartedAtNanos);

        var parseStartedAtNanos = System.nanoTime();
        var ocrScanResult = parseMistralResponse(responseBody);
        var parseElapsedMs = elapsedMillis(parseStartedAtNanos);

        logger.info(
                "Nutrition scan image completed requestMs={} parseMs={} nutritionSource={} productSource={} annotationFallback={} productAnnotationFallback={} totalWeight={} servingsPerContainer100={}",
                requestElapsedMs,
                parseElapsedMs,
                ocrScanResult.sourceUsed(),
                ocrScanResult.productSourceUsed(),
                ocrScanResult.usedAnnotationFallback(),
                ocrScanResult.productUsedAnnotationFallback(),
                ocrScanResult.product().totalWeight(),
                ocrScanResult.product().getServingsPerContainer100()
        );

        return new SingleImageScanResult(ocrScanResult, requestElapsedMs, parseElapsedMs);
    }

    private AsyncMergeResult processImagesAsync(List<MultipartFile> normalizedImages) {
        var imageCompletableFutures = normalizedImages.stream()
                .map(image -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return requestAndParseSingleImage(image);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }))
                .toList();

        ScanResult mergedImageInfo = null;
        var totalRequestMs = 0L;
        var totalParseMs = 0L;
        var imageErrors = new ArrayList<String>();
        for (var future : imageCompletableFutures) {
            try {
                var imageResult = future.join();
                totalRequestMs += imageResult.requestElapsedMs();
                totalParseMs += imageResult.parseElapsedMs();
                mergedImageInfo = mergedImageInfo == null ? imageResult.result() : scanResultMerger.mergeScanResults(mergedImageInfo, imageResult.result());
            } catch (CompletionException e) {
                var cause = e.getCause();
                var detail = cause != null ? cause.getMessage() : "Unknown error";
                imageErrors.add("Image scan failed: " + detail);
                logger.warn("Per-image scan failed error={}", detail);
            }
        }

        if (mergedImageInfo == null) {
            var allWarnings = new ArrayList<String>();
            allWarnings.add("Nutrition scan failed");
            allWarnings.addAll(imageErrors);
            return new AsyncMergeResult(ScanResult.failed(allWarnings), totalRequestMs, totalParseMs);
        }

        if (!imageErrors.isEmpty()) {
            var combinedWarnings = new ArrayList<String>();
            combinedWarnings.addAll(mergedImageInfo.warnings());
            combinedWarnings.addAll(imageErrors);
            mergedImageInfo = new ScanResult(
                    mergedImageInfo.scanSucceeded(),
                    List.copyOf(combinedWarnings),
                    mergedImageInfo.disagreements(),
                    mergedImageInfo.productDisagreements(),
                    mergedImageInfo.sourceUsed(),
                    mergedImageInfo.usedAnnotationFallback(),
                    mergedImageInfo.productSourceUsed(),
                    mergedImageInfo.productUsedAnnotationFallback(),
                    mergedImageInfo.parsed(),
                    mergedImageInfo.product(),
                    mergedImageInfo.rawNutrients()
            );
        }

        return new AsyncMergeResult(mergedImageInfo, totalRequestMs, totalParseMs);
    }

    ScanResult parseMistralResponse(String body) throws IOException {
        var root = objectMapper.readTree(body);
        var warnings = new ArrayList<String>();
        var nutritionDisagreements = new ArrayList<String>();
        var productDisagreements = new ArrayList<String>();

        var rawNutrients = new LinkedHashMap<String, RawNutrient>();

        var rawNutrition = parseNutritionFromPages(root.path("pages"), rawNutrients);
        var rawProduct = parseProductFromPages(root.path("pages"));
        var annotationNode = annotationParser.parse(root.path("document_annotation"), warnings);
        var annotationNutrition = annotationParser.parseNutrition(annotationNode, new LinkedHashMap<>());
        var annotationProduct = annotationParser.parseProduct(annotationNode);

        logAnnotationSnapshot(annotationNutrition, annotationProduct);

        var merged = mergeParsedResults(annotationNutrition, rawNutrition, annotationProduct, rawProduct, warnings, nutritionDisagreements, productDisagreements);

        logMergedResultSnapshot(merged.nutrition(), merged.product(), annotationNutrition, annotationProduct, rawNutrition, rawProduct);

        var flags = resolveScanFlags(merged.nutrition(), annotationNutrition, rawNutrition, merged.product(), annotationProduct, rawProduct);
        return buildFinalResult(flags, warnings, nutritionDisagreements, productDisagreements, merged.nutrition(), merged.product(), rawNutrients);
    }

    private void logAnnotationSnapshot(ParsedNutrition annotationNutrition, ProductDetails annotationProduct) {
        logger.info(
                "Mistral mapped annotation nutrition servingSize={} servingUnit={} kcalServing={} kcalPer100={} proteinPer100={} carbsPer100={} fatPer100={} servingsPerContainer={} totalWeight={} totalWeightUnit={} drainedWeight={} drainedWeightUnit={}",
                annotationNutrition.servingSize(),
                annotationNutrition.servingUnit(),
                annotationNutrition.caloriesPerServing(),
                annotationNutrition.caloriesPer100(),
                annotationNutrition.proteinPer100(),
                annotationNutrition.carbsPer100(),
                annotationNutrition.fatPer100(),
                annotationProduct.servingsPerContainer(),
                annotationProduct.totalWeight(),
                annotationProduct.totalWeightUnit(),
                annotationProduct.drainedWeight(),
                annotationProduct.drainedWeightUnit()
        );
    }

    private record MergedOcrResult(ParsedNutrition nutrition, ProductDetails product) {
    }

    private MergedOcrResult mergeParsedResults(
            ParsedNutrition annotationNutrition,
            ParseOutcome rawNutrition,
            ProductDetails annotationProduct,
            ProductParseOutcome rawProduct,
            List<String> warnings,
            List<String> nutritionDisagreements,
            List<String> productDisagreements
    ) {
        var parsedNutrition = scanResultMerger.mergePreferAnnotatedNutrition(annotationNutrition, rawNutrition.parsed(), warnings, nutritionDisagreements);
        var parsedProduct = scanResultMerger.mergePreferAnnotatedProduct(annotationProduct, rawProduct.product(), warnings, productDisagreements);
        parsedProduct = scanResultMerger.resolveServingsPerContainer(
                parsedNutrition,
                parsedProduct,
                rawProduct.product(),
                annotationProduct,
                warnings,
                productDisagreements
        );
        parsedProduct = scanResultMerger.resolveDerivedProductWeight(parsedNutrition, parsedProduct);
        return new MergedOcrResult(parsedNutrition, parsedProduct);
    }

    private void logMergedResultSnapshot(
            ParsedNutrition parsedNutrition,
            ProductDetails parsedProduct,
            ParsedNutrition annotationNutrition,
            ProductDetails annotationProduct,
            ParseOutcome rawNutrition,
            ProductParseOutcome rawProduct
    ) {
        logger.info(
                "Mistral merged mapped result nutritionSource={} productSource={} servingSize={} servingUnit={} kcalServing={} kcalPer100={} servingsPerContainer={} totalWeight={} totalWeightUnit={} drainedWeight={} drainedWeightUnit={}",
                ScanUtils.hasParsedNutrition(annotationNutrition) ? ScanSource.ANNOTATION : rawNutrition.source(),
                ScanUtils.hasProductValues(annotationProduct) ? ScanSource.ANNOTATION : rawProduct.source(),
                parsedNutrition.servingSize(),
                parsedNutrition.servingUnit(),
                parsedNutrition.caloriesPerServing(),
                parsedNutrition.caloriesPer100(),
                parsedProduct.servingsPerContainer(),
                parsedProduct.totalWeight(),
                parsedProduct.totalWeightUnit(),
                parsedProduct.drainedWeight(),
                parsedProduct.drainedWeightUnit()
        );
    }

    private ScanFlags resolveScanFlags(
            ParsedNutrition parsedNutrition,
            ParsedNutrition annotationNutrition,
            ParseOutcome rawNutrition,
            ProductDetails parsedProduct,
            ProductDetails annotationProduct,
            ProductParseOutcome rawProduct
    ) {
        var nutritionHasValues = ScanUtils.hasParsedNutrition(parsedNutrition);
        var productHasValues = ScanUtils.hasProductValues(parsedProduct);
        return new ScanFlags(
                nutritionHasValues,
                productHasValues,
                resolveScanSource(nutritionHasValues, ScanUtils.hasParsedNutrition(annotationNutrition), rawNutrition.source()),
                resolveScanSource(productHasValues, ScanUtils.hasProductValues(annotationProduct), rawProduct.source()),
                usedNutritionAnnotationFallback(rawNutrition.parsed(), annotationNutrition),
                usedProductAnnotationFallback(rawProduct.product(), annotationProduct)
        );
    }

    private ScanResult buildFinalResult(
            ScanFlags flags,
            List<String> warnings,
            List<String> nutritionDisagreements,
            List<String> productDisagreements,
            ParsedNutrition parsedNutrition,
            ProductDetails parsedProduct,
            LinkedHashMap<String, RawNutrient> rawNutrients
    ) {
        if (!flags.nutritionHasValues && !flags.productHasValues) {
            warnings.add("Unable to extract useful OCR values");
        }
        return new ScanResult(
                flags.nutritionHasValues || flags.productHasValues,
                List.copyOf(warnings),
                List.copyOf(nutritionDisagreements),
                List.copyOf(productDisagreements),
                flags.nutritionSource,
                flags.nutritionAnnotationFallback,
                flags.productSource,
                flags.productAnnotationFallback,
                parsedNutrition,
                parsedProduct,
                Map.copyOf(rawNutrients)
        );
    }

    private record ScanFlags(
            boolean nutritionHasValues,
            boolean productHasValues,
            ScanSource nutritionSource,
            ScanSource productSource,
            boolean nutritionAnnotationFallback,
            boolean productAnnotationFallback
    ) {
    }

    // todo not sure if we ever use this. When would this even be populated
    private ParseOutcome parseNutritionFromPages(JsonNode pagesNode, Map<String, RawNutrient> rawNutrients) {
        if (pagesNode == null || !pagesNode.isArray() || pagesNode.isEmpty()) {
            return new ParseOutcome(ScanSource.NONE, ParsedNutrition.empty());
        }

        var page = pagesNode.get(0); // we only scan one at a time for now
        if (page == null || page.isNull()) {
            return new ParseOutcome(ScanSource.NONE, ParsedNutrition.empty());
        }

        var tableParsed = tableParser.parseNutritionFromTables(page.path("tables"), rawNutrients);
        if (ScanUtils.hasParsedNutrition(tableParsed)) {
            return new ParseOutcome(ScanSource.RAW_TABLE, tableParsed);
        }

        var markdownParsed = nutritionMarkdownParser.parseNutritionFromMarkdown(page.path("markdown").asText(""), rawNutrients);
        if (ScanUtils.hasParsedNutrition(markdownParsed)) {
            return new ParseOutcome(ScanSource.RAW_TEXT, markdownParsed);
        }

        return new ParseOutcome(ScanSource.NONE, ParsedNutrition.empty());
    }

    private ProductParseOutcome parseProductFromPages(JsonNode pagesNode) {
        if (pagesNode == null || !pagesNode.isArray() || pagesNode.isEmpty()) {
            return new ProductParseOutcome(ScanSource.NONE, ProductDetails.empty());
        }

        var page = pagesNode.get(0);
        if (page == null || page.isNull()) {
            return new ProductParseOutcome(ScanSource.NONE, ProductDetails.empty());
        }

        var productDetails = productTextParser.parseProductFromMarkdown(page.path("markdown").asText(""));
        return new ProductParseOutcome(ScanUtils.hasProductValues(productDetails) ? ScanSource.RAW_TEXT : ScanSource.NONE, productDetails);
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private ScanSource resolveScanSource(boolean hasValues, boolean annotationHasValues, ScanSource rawSource) {
        if (!hasValues) {
            return ScanSource.NONE;
        }
        return annotationHasValues ? ScanSource.ANNOTATION : rawSource;
    }

    private boolean usedNutritionAnnotationFallback(ParsedNutrition rawNutrition, ParsedNutrition annotationNutrition) {
        return ScanUtils.hasParsedNutrition(rawNutrition)
                && ScanUtils.hasParsedNutrition(annotationNutrition)
                && ScanUtils.hasMissingNutritionFields(annotationNutrition);
    }

    private boolean usedProductAnnotationFallback(ProductDetails rawProduct, ProductDetails annotationProduct) {
        return ScanUtils.hasProductValues(annotationProduct)
                && (rawProduct == null
                || rawProduct.name() == null
                || rawProduct.brand() == null
                || rawProduct.storeName() == null
                || rawProduct.servingsPerContainer() == null
                || rawProduct.totalWeight() == null
                || rawProduct.totalWeightUnit() == null);
    }

    private record ParseOutcome(ScanSource source, ParsedNutrition parsed) {
    }

    private record ProductParseOutcome(ScanSource source, ProductDetails product) {
    }

    private record SingleImageScanResult(ScanResult result, long requestElapsedMs, long parseElapsedMs) {
    }

    private record AsyncMergeResult(ScanResult result, long totalRequestMs, long totalParseMs) {
    }

    private record WeightMatch(Float normalizedValue, FoodEntity.Unit unit) {
    }

}
