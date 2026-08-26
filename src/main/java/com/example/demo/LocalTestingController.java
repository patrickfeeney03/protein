package com.example.demo;

import com.example.demo.DTOs.CommonScrappedDTO;
import com.example.demo.services.UserService;
import com.example.demo.services.NutritionScanService;
import com.example.demo.services.ScanResult;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

@RestController
@RequestMapping("/testing")
@Profile("dev")
public class LocalTestingController {

    private final AldiFoodScraper aldiFoodScraper;
    private final AldiPriceFetcher aldiPriceFetcher;
    private final AldiPriceUpdater aldiPriceUpdater;
    private final LidlPriceFetcher lidlPriceFetcher;
    private final LidlPriceUpdater lidlPriceUpdater;
    private final DunnesPriceFetcher dunnesPriceFetcher;
    private final DunnesPriceUpdater dunnesPriceUpdater;
    private final TescoPriceFetcher tescoPriceFetcher;
    private final TescoPriceUpdater tescoPriceUpdater;
    private final NutritionScanService nutritionScanService;
    private final UserService userService;

    public LocalTestingController(
            AldiFoodScraper aldiFoodScraper,
            AldiPriceFetcher aldiPriceFetcher,
            AldiPriceUpdater aldiPriceUpdater,
            LidlPriceFetcher lidlPriceFetcher,
            LidlPriceUpdater lidlPriceUpdater,
            DunnesPriceFetcher dunnesPriceFetcher,
            DunnesPriceUpdater dunnesPriceUpdater,
            TescoPriceFetcher tescoPriceFetcher,
            TescoPriceUpdater tescoPriceUpdater,
            NutritionScanService nutritionScanService,
            UserService userService
    ) {
        this.aldiFoodScraper = aldiFoodScraper;
        this.aldiPriceFetcher = aldiPriceFetcher;
        this.aldiPriceUpdater = aldiPriceUpdater;
        this.lidlPriceFetcher = lidlPriceFetcher;
        this.lidlPriceUpdater = lidlPriceUpdater;
        this.dunnesPriceFetcher = dunnesPriceFetcher;
        this.dunnesPriceUpdater = dunnesPriceUpdater;
        this.tescoPriceFetcher = tescoPriceFetcher;
        this.tescoPriceUpdater = tescoPriceUpdater;
        this.nutritionScanService = nutritionScanService;
        this.userService = userService;
    }

    @PostMapping("/aldi")
    public List<CommonScrappedDTO> aldiProducts(Authentication authentication) {
        requireAdmin(authentication);
        return this.aldiFoodScraper.getData();
    }

    @GetMapping("/aldi/price")
    public PriceLookup aldiPrice(@RequestParam String url, Authentication authentication) {
        requireAdmin(authentication);
        var price = this.aldiPriceFetcher.fetchPrice(url).orElse(null);
        return new PriceLookup(url, price);
    }

    @PostMapping("/aldi/refresh-prices")
    public RefreshResult refreshAldiPrices(Authentication authentication) {
        requireAdmin(authentication);
        var updated = runLikeScheduledJob(this.aldiPriceUpdater::refreshPrices);
        return new RefreshResult(updated);
    }

    @GetMapping("/lidl/price")
    public PriceLookup lidlPrice(@RequestParam String url, Authentication authentication) {
        requireAdmin(authentication);
        var price = this.lidlPriceFetcher.fetchPrice(url).orElse(null);
        return new PriceLookup(url, price);
    }

    @PostMapping("/lidl/refresh-prices")
    public RefreshResult refreshLidlPrices(Authentication authentication) {
        requireAdmin(authentication);
        var updated = runLikeScheduledJob(this.lidlPriceUpdater::refreshPrices);
        return new RefreshResult(updated);
    }

    @GetMapping("/dunnes/price")
    public PriceLookup dunnesPrice(@RequestParam String url, Authentication authentication) {
        requireAdmin(authentication);
        var price = this.dunnesPriceFetcher.fetchPrice(url).orElse(null);
        return new PriceLookup(url, price);
    }

    @GetMapping("/tesco/price")
    public PriceLookup tescoPrice(@RequestParam String url, Authentication authentication) {
        requireAdmin(authentication);
        var price = this.tescoPriceFetcher.fetchPrice(url).orElse(null);
        return new PriceLookup(url, price);
    }

    @PostMapping("/dunnes/refresh-prices")
    public RefreshResult refreshDunnesPrices(Authentication authentication) {
        requireAdmin(authentication);
        var updated = runLikeScheduledJob(this.dunnesPriceUpdater::refreshPrices);
        return new RefreshResult(updated);
    }

    @PostMapping("/tesco/refresh-prices")
    public RefreshResult refreshTescoPrices(Authentication authentication) {
        requireAdmin(authentication);
        var updated = runLikeScheduledJob(this.tescoPriceUpdater::refreshPrices);
        return new RefreshResult(updated);
    }

    @PostMapping(path = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ScanResult scanNutritionLabel(
            @RequestPart(name = "images", required = false) List<MultipartFile> images,
            @RequestPart(name = "image", required = false) MultipartFile image,
            Authentication authentication
    ) {
        requireAdmin(authentication);
        return this.nutritionScanService.scan(MultipartImageNormalizer.normalize(images, image));
    }

    private int runLikeScheduledJob(Supplier<Integer> refreshAction) {
        try {
            return CompletableFuture.supplyAsync(refreshAction).join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }
    }

    private void requireAdmin(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        var email = authentication.getName();
        if (email == null || email.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        var user = userService.getByEmail(email)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED));

        if (!user.isAdmin()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private record PriceLookup(String url, Float price) {}
    private record RefreshResult(int updated) {}


}
