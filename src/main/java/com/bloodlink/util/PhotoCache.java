package com.bloodlink.util;

import com.bloodlink.dao.UserDAO;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory cache for user profile photos to prevent N+1 database blob fetches
 * and high memory usage when displaying multiple profile cards.
 */
public class PhotoCache {

    private static final Map<Long, Image> cache = new ConcurrentHashMap<>();
    private static final Map<Long, Boolean> noPhotoCache = new ConcurrentHashMap<>();
    private static UserDAO userDAO;

    public static void setUserDAO(UserDAO dao) {
        userDAO = dao;
    }

    /**
     * Retrieves the profile photo for a user. Caches the result to avoid repeated DB calls.
     */
    public static Image getPhotoSync(long userId) {
        if (cache.containsKey(userId)) {
            return cache.get(userId);
        }
        if (noPhotoCache.containsKey(userId)) {
            return null;
        }
        
        if (userDAO != null) {
            try {
                Optional<byte[]> photoBytesOpt = userDAO.findPhoto(userId);
                if (photoBytesOpt.isPresent() && photoBytesOpt.get().length > 0) {
                    Image img = new Image(new ByteArrayInputStream(photoBytesOpt.get()));
                    cache.put(userId, img);
                    return img;
                } else {
                    noPhotoCache.put(userId, true);
                }
            } catch (Exception e) {
                System.err.println("Error fetching photo for user " + userId + ": " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Clears the cache for a specific user (e.g., when they upload a new photo).
     */
    public static void invalidate(long userId) {
        cache.remove(userId);
        noPhotoCache.remove(userId);
    }

    /**
     * Clears the entire cache.
     */
    public static void clear() {
        cache.clear();
        noPhotoCache.clear();
    }
}
