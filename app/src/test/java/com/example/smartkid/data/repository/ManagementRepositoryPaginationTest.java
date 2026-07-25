package com.example.smartkid.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ManagementRepositoryPaginationTest {
    private static final String BASE_URL = "https://smartkid.example/api/";

    @Test
    public void resolveNextEndpoint_convertsAbsoluteDrfUrlAndKeepsQuery() throws Exception {
        String endpoint = ManagementRepository.resolveNextEndpoint(
                "content/courses/?page=1&pageSize=100",
                "https://smartkid.example/api/content/courses/?page=2&pageSize=100&search=toan",
                BASE_URL);

        assertEquals("content/courses/?page=2&pageSize=100&search=toan", endpoint);
    }

    @Test
    public void resolveNextEndpoint_resolvesQueryOnlyAgainstCurrentEndpoint() throws Exception {
        String endpoint = ManagementRepository.resolveNextEndpoint(
                "content/modules/abc/lessons/?page=1",
                "?page=2&status=published",
                BASE_URL);

        assertEquals("content/modules/abc/lessons/?page=2&status=published", endpoint);
    }

    @Test
    public void resolveNextEndpoint_acceptsRootRelativeApiUrl() throws Exception {
        String endpoint = ManagementRepository.resolveNextEndpoint(
                "content/courses/?page=1",
                "/api/content/courses/?page=2",
                BASE_URL);

        assertEquals("content/courses/?page=2", endpoint);
    }

    @Test(expected = IllegalArgumentException.class)
    public void resolveNextEndpoint_rejectsForeignOrigin() throws Exception {
        ManagementRepository.resolveNextEndpoint(
                "content/courses/?page=1",
                "https://attacker.example/api/content/courses/?page=2",
                BASE_URL);
    }

    @Test(expected = IllegalArgumentException.class)
    public void resolveNextEndpoint_rejectsPathOutsideConfiguredApi() throws Exception {
        ManagementRepository.resolveNextEndpoint(
                "content/courses/?page=1",
                "https://smartkid.example/private/courses/?page=2",
                BASE_URL);
    }

    @Test
    public void paginationState_rejectsRepeatedPagesAndCompletesOnlyOnce() {
        ManagementRepository.PaginationState state =
                new ManagementRepository.PaginationState();

        assertTrue(state.visit("content/courses/?page=1"));
        assertFalse(state.visit("content/courses/?page=1"));
        assertEquals(1, state.getPageCount());
        assertTrue(state.finish());
        assertFalse(state.finish());
        assertFalse(state.visit("content/courses/?page=2"));
    }
}
