package com.example.demo.dedup;

import com.example.demo.entities.FoodEntity;
import com.example.demo.repositories.FoodRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class FoodDedupService {
    private static final Pattern TESCO_TPNC_PATTERN = Pattern.compile("/products/(\\d+)");
    private static final Pattern LIDL_PRODUCT_PATTERN = Pattern.compile("/p(\\d+)$");
    private static final Pattern DUNNES_STORE_PATTERN = Pattern.compile("/rsid/(\\d+)");
    private static final Pattern DUNNES_PRODUCT_PATTERN = Pattern.compile("id-(\\d+)");
    private static final Pattern ALDI_SKU_PATTERN = Pattern.compile("-(\\d+)$");

    private static final double POSSIBLE_DUPLICATE_THRESHOLD = 0.88d;
    private static final double CANDIDATE_FLOOR = 0.65d;
    private static final double BRAND_TYPO_MATCH_THRESHOLD = 0.85d;
    private static final double MACRO_ABSOLUTE_TOLERANCE = 1.0d;
    private static final int MAX_CANDIDATE_POOL = 200;

    private static final double NAME_WEIGHT = 0.25d;
    private static final double BRAND_WEIGHT = 0.15d;
    private static final double MACRO_WEIGHT = 0.35d;
    private static final double WEIGHT_WEIGHT = 0.15d;
    private static final double PRICE_WEIGHT = 0.10d;

    private final FoodRepository foodRepository;

    public FoodDedupService(FoodRepository foodRepository) {
        this.foodRepository = foodRepository;
    }

    public Optional<String> computeCanonicalProductKey(String productUrl) {
        if (productUrl == null || productUrl.isBlank()) {
            return Optional.empty();
        }

        var normalizedUrl = normalizeUrl(productUrl);
        if (normalizedUrl.isEmpty()) {
            return Optional.empty();
        }

        var uri = normalizedUrl.get();
        var host = normalizeHost(uri.getHost());
        var path = normalizePath(uri.getPath());

        if (isRetailerHost(host, "tesco.ie")) {
            var tpnc = extract(path, TESCO_TPNC_PATTERN);
            if (tpnc.isPresent()) {
                return Optional.of("tesco:" + tpnc.get());
            }
        }

        if (isRetailerHost(host, "lidl.ie")) {
            var productId = extract(path, LIDL_PRODUCT_PATTERN);
            if (productId.isPresent()) {
                return Optional.of("lidl:" + productId.get());
            }
        }

        if (isRetailerHost(host, "dunnesstoresgrocery.com")) {
            var storeId = extract(path, DUNNES_STORE_PATTERN);
            var productId = extract(path, DUNNES_PRODUCT_PATTERN);
            if (storeId.isPresent() && productId.isPresent()) {
                return Optional.of("dunnes:" + storeId.get() + ":" + productId.get());
            }
            if (productId.isPresent()) {
                return Optional.of("dunnes:" + productId.get());
            }
        }

        if (isRetailerHost(host, "aldi.ie")) {
            var sku = extract(path, ALDI_SKU_PATTERN);
            if (sku.isPresent()) {
                return Optional.of("aldi:" + sku.get());
            }
        }

        var hashInput = host + path;
        return Optional.of("urlhash:" + shortHash(hashInput));
    }

    public Optional<FoodEntity> findExactDuplicate(String canonicalProductKey) {
        if (canonicalProductKey == null || canonicalProductKey.isBlank()) {
            return Optional.empty();
        }
        return foodRepository.findFirstByCanonicalProductKey(canonicalProductKey);
    }

    public List<DedupCandidate> findLikelyDuplicates(
            FoodEntity.FoodRequest request,
            Float caloriesPer100,
            Float proteinPer100,
            Float carbsPer100,
            Float fatPer100,
            Float totalWeight,
            Float price
    ) {
        var normalizedName = normalizeText(request.name());
        var normalizedBrand = normalizeText(request.brand());

        if (normalizedName.isBlank() && normalizedBrand.isBlank()) {
            return List.of();
        }

        var candidates = collectCandidates(normalizedName, normalizedBrand);

        return candidates.stream()
                .map(existing -> scoreCandidate(
                        existing,
                        normalizedName,
                        normalizedBrand,
                        caloriesPer100,
                        proteinPer100,
                        carbsPer100,
                        fatPer100,
                        totalWeight,
                        price
                ))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(candidate -> candidate.score() >= CANDIDATE_FLOOR)
                .sorted(Comparator.comparingDouble(DedupCandidate::score).reversed())
                .limit(5)
                .toList();
    }

    public boolean isPossibleDuplicate(List<DedupCandidate> candidates) {
        return candidates.stream().anyMatch(candidate -> candidate.score() >= POSSIBLE_DUPLICATE_THRESHOLD);
    }

    private Optional<DedupCandidate> scoreCandidate(
            FoodEntity existing,
            String normalizedName,
            String normalizedBrand,
            Float caloriesPer100,
            Float proteinPer100,
            Float carbsPer100,
            Float fatPer100,
            Float totalWeight,
            Float price
    ) {
        double weightedScoreSum = 0d;
        double weightSum = 0d;
        var reasons = new ArrayList<String>();

        var existingName = normalizeText(existing.getName());
        var existingBrand = normalizeText(existing.getBrand());

        boolean sameName = !normalizedName.isBlank() && normalizedName.equals(existingName);
        var nameSimilarity = stringSimilarity(normalizedName, existingName);
        weightedScoreSum += nameSimilarity * NAME_WEIGHT;
        weightSum += NAME_WEIGHT;
        if (nameSimilarity >= 0.90d) {
            reasons.add("name");
        }
        if (sameName) {
            reasons.add("same_name");
        }

        var brandSimilarity = stringSimilarity(normalizedBrand, existingBrand);
        weightedScoreSum += brandSimilarity * BRAND_WEIGHT;
        weightSum += BRAND_WEIGHT;
        if (brandSimilarity >= 0.90d) {
            reasons.add("brand");
        }
        boolean brandTypoMatch = !normalizedBrand.isBlank()
                && !existingBrand.isBlank()
                && brandSimilarity >= BRAND_TYPO_MATCH_THRESHOLD;
        if (brandTypoMatch) {
            reasons.add("brand_typo_match");
        }

        var macroScore = average(
                numericSimilarity(caloriesPer100, existing.getCaloriesPer100(), 0.06d),
                numericSimilarity(proteinPer100, existing.getProteinPer100(), 0.06d),
                numericSimilarity(carbsPer100, existing.getCarbsPer100(), 0.06d),
                numericSimilarity(fatPer100, existing.getFatPer100(), 0.06d)
        );
        if (macroScore.isPresent()) {
            weightedScoreSum += macroScore.get() * MACRO_WEIGHT;
            weightSum += MACRO_WEIGHT;
            if (macroScore.get() >= 0.92d) {
                reasons.add("macros");
            }
        }
        boolean macrosWithinTolerance = areMacrosWithinTolerance(
                caloriesPer100,
                proteinPer100,
                carbsPer100,
                fatPer100,
                existing.getCaloriesPer100(),
                existing.getProteinPer100(),
                existing.getCarbsPer100(),
                existing.getFatPer100()
        );
        if (macrosWithinTolerance) {
            reasons.add("macros_close");
        }

        var weightSimilarity = numericSimilarity(totalWeight, existing.getTotalWeight(), 0.08d);
        if (weightSimilarity.isPresent()) {
            weightedScoreSum += weightSimilarity.get() * WEIGHT_WEIGHT;
            weightSum += WEIGHT_WEIGHT;
            if (weightSimilarity.get() >= 0.92d) {
                reasons.add("weight");
            }
        }

        var priceSimilarity = numericSimilarity(price, existing.getPrice(), 0.20d);
        if (priceSimilarity.isPresent()) {
            weightedScoreSum += priceSimilarity.get() * PRICE_WEIGHT;
            weightSum += PRICE_WEIGHT;
            if (priceSimilarity.get() >= 0.95d) {
                reasons.add("price");
            }
        }

        if (weightSum == 0d) {
            return Optional.empty();
        }

        var finalScore = weightedScoreSum / weightSum;
        boolean weightVeryClose = weightSimilarity.orElse(0d) >= 0.97d;
        boolean priceVeryClose = numericSimilarity(price, existing.getPrice(), 0.05d).orElse(0d) >= 0.97d;
        if (macrosWithinTolerance && weightVeryClose && priceVeryClose) {
            reasons.add("numeric_high_confidence");
            finalScore = Math.max(finalScore, POSSIBLE_DUPLICATE_THRESHOLD + 0.01d);
        }
        if (sameName && (brandTypoMatch || macrosWithinTolerance)) {
            finalScore = Math.max(finalScore, POSSIBLE_DUPLICATE_THRESHOLD + 0.01d);
        }
        return Optional.of(new DedupCandidate(
                existing.getId(),
                existing.getName(),
                existing.getBrand(),
                finalScore,
                reasons
        ));
    }

    private List<FoodEntity> collectCandidates(String normalizedName, String normalizedBrand) {
        Map<Long, FoodEntity> byId = new LinkedHashMap<>();

        if (!normalizedName.isBlank() && !normalizedBrand.isBlank()) {
            addCandidates(byId, foodRepository.findTop100ByNameContainingIgnoreCaseOrBrandContainingIgnoreCase(
                    normalizedName,
                    normalizedBrand
            ));
        } else if (!normalizedName.isBlank()) {
            addCandidates(byId, foodRepository.findTop100ByNameContainingIgnoreCase(normalizedName));
        } else if (!normalizedBrand.isBlank()) {
            addCandidates(byId, foodRepository.findTop100ByBrandContainingIgnoreCase(normalizedBrand));
        }

        for (var token : strongestTokens(normalizedName, 4)) {
            addCandidates(byId, foodRepository.findTop100ByNameContainingIgnoreCase(token));
            if (!normalizedBrand.isBlank()) {
                addCandidates(byId, foodRepository.findTop100ByNameContainingIgnoreCaseOrBrandContainingIgnoreCase(
                        token,
                        normalizedBrand
                ));
            }
            if (byId.size() >= MAX_CANDIDATE_POOL) {
                break;
            }
        }

        return byId.values().stream().limit(MAX_CANDIDATE_POOL).toList();
    }

    private void addCandidates(Map<Long, FoodEntity> byId, List<FoodEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (var candidate : candidates) {
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            byId.putIfAbsent(candidate.getId(), candidate);
            if (byId.size() >= MAX_CANDIDATE_POOL) {
                return;
            }
        }
    }

    private List<String> strongestTokens(String normalizedName, int limit) {
        if (normalizedName == null || normalizedName.isBlank()) {
            return List.of();
        }
        return List.of(normalizedName.split("\\s+")).stream()
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .distinct()
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .limit(limit)
                .toList();
    }

    private boolean areMacrosWithinTolerance(
            Float caloriesA,
            Float proteinA,
            Float carbsA,
            Float fatA,
            Float caloriesB,
            Float proteinB,
            Float carbsB,
            Float fatB
    ) {
        return withinTolerance(caloriesA, caloriesB)
                && withinTolerance(proteinA, proteinB)
                && withinTolerance(carbsA, carbsB)
                && withinTolerance(fatA, fatB);
    }

    private boolean withinTolerance(Float left, Float right) {
        if (left == null || right == null) {
            return false;
        }
        return Math.abs(left - right) <= MACRO_ABSOLUTE_TOLERANCE;
    }

    private Optional<Double> average(Optional<Double>... values) {
        double sum = 0d;
        int count = 0;
        for (var value : values) {
            if (value.isPresent()) {
                sum += value.get();
                count++;
            }
        }
        if (count == 0) {
            return Optional.empty();
        }
        return Optional.of(sum / count);
    }

    private Optional<Double> numericSimilarity(Float left, Float right, double tolerancePercent) {
        if (left == null || right == null) {
            return Optional.empty();
        }

        var a = Math.abs(left);
        var b = Math.abs(right);
        var max = Math.max(a, b);

        if (max == 0f) {
            return Optional.of(1d);
        }

        var delta = Math.abs(left - right);
        var tolerance = max * tolerancePercent;
        if (delta <= tolerance) {
            return Optional.of(1d);
        }

        var score = 1d - Math.min(1d, (delta - tolerance) / (max + 1f));
        return Optional.of(Math.max(0d, score));
    }

    private double stringSimilarity(String left, String right) {
        if (left.isBlank() && right.isBlank()) {
            return 1d;
        }
        if (left.isBlank() || right.isBlank()) {
            return 0d;
        }
        if (left.equals(right)) {
            return 1d;
        }
        if (left.contains(right) || right.contains(left)) {
            return 0.92d;
        }

        var leftBigrams = bigrams(left);
        var rightBigrams = bigrams(right);
        if (leftBigrams.isEmpty() || rightBigrams.isEmpty()) {
            return 0d;
        }

        int overlap = 0;
        for (var leftBigram : leftBigrams) {
            if (rightBigrams.contains(leftBigram)) {
                overlap++;
            }
        }

        return (2d * overlap) / (leftBigrams.size() + rightBigrams.size());
    }

    private List<String> bigrams(String value) {
        var compact = value.replace(" ", "");
        if (compact.length() < 2) {
            return List.of();
        }

        var result = new ArrayList<String>();
        for (int i = 0; i < compact.length() - 1; i++) {
            result.add(compact.substring(i, i + 2));
        }
        return result;
    }

    private Optional<URI> normalizeUrl(String rawUrl) {
        try {
            var trimmed = rawUrl.trim();
            if (!trimmed.matches("^[a-zA-Z][a-zA-Z0-9+\\-.]*://.*$")) {
                trimmed = "https://" + trimmed;
            }

            var uri = URI.create(trimmed);
            var scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            var host = normalizeHost(uri.getHost());
            if (host.isBlank()) {
                return Optional.empty();
            }
            var path = normalizePath(uri.getPath());
            return Optional.of(new URI(scheme, host, path, null, null));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private boolean isRetailerHost(String host, String baseDomain) {
        if (host == null || host.isBlank()) {
            return false;
        }
        return host.equals(baseDomain) || host.endsWith("." + baseDomain);
    }

    private String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        var normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("www.")) {
            return normalized.substring(4);
        }
        return normalized;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        var normalized = path.trim();
        if (normalized.endsWith("/") && normalized.length() > 1) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private Optional<String> extract(String input, Pattern pattern) {
        var matcher = pattern.matcher(input);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }

    private String shortHash(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
