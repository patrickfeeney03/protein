package com.example.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetailerUrlPolicyTest {
    @Test
    void acceptsExactHttpsRetailerHost() {
        assertThat(RetailerUrlPolicy.retailerFor("https://www.tesco.ie/groceries/en-IE/products/1"))
                .contains(RetailerUrlPolicy.Retailer.TESCO);
    }

    @Test
    void rejectsSubstringAndNonHttpsHosts() {
        assertThat(RetailerUrlPolicy.retailerFor("https://www.tesco.ie.attacker.example/products/1")).isEmpty();
        assertThat(RetailerUrlPolicy.retailerFor("http://www.tesco.ie/products/1")).isEmpty();
        assertThat(RetailerUrlPolicy.retailerFor("https://user:pass@www.tesco.ie/products/1")).isEmpty();
    }

    @Test
    void browserAllowlistRequiresExactHostAndHttps() {
        var allowed = java.util.Set.of("www.dunnesstoresgrocery.com");
        assertThat(RetailerUrlPolicy.isAllowedBrowserUrl(
                "https://www.dunnesstoresgrocery.com/path", allowed)).isTrue();
        assertThat(RetailerUrlPolicy.isAllowedBrowserUrl(
                "https://www.dunnesstoresgrocery.com.attacker.example/path", allowed)).isFalse();
        assertThat(RetailerUrlPolicy.isAllowedBrowserUrl(
                "http://www.dunnesstoresgrocery.com/path", allowed)).isFalse();
    }
}
