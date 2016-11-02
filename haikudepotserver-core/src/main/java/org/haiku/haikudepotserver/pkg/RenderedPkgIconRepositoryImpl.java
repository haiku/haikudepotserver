/*
 * Copyright 2015-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.ByteStreams;
import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.MediaType;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgIcon;
import org.haiku.haikudepotserver.graphics.hvif.HvifRenderingService;
import org.haiku.haikudepotserver.graphics.bitmap.PngOptimizationService;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Repository
public class RenderedPkgIconRepositoryImpl implements RenderedPkgIconRepository {

    @Resource
    private HvifRenderingService hvifRenderingService;

    @Resource
    private PngOptimizationService pngOptimizationService;

    private byte[] genericHvif;

    private Cache<String, Cache<Integer, Optional<byte[]>>> cache = CacheBuilder
            .newBuilder()
            .maximumSize(256)
            .expireAfterAccess(12, TimeUnit.HOURS)
            .build();

    /**
     * <p>Holds a cache of generic icons rather than those that are specific to a given package.</p>
     */

    private Cache<Integer, byte[]> genericCache = CacheBuilder
            .newBuilder()
            .maximumSize(10)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    private Cache<Integer, Optional<byte[]>> getOrCreatePkgCache(String name) {
        try {
            return cache.get(name, () -> CacheBuilder
                    .newBuilder()
                    .maximumSize(10)
                    .expireAfterAccess(30, TimeUnit.MINUTES)
                    .build());
        }
        catch(Exception e) {
            throw new RuntimeException("unable to get or create the package to icon cache.", e);
        }
    }

    @Override
    public void evict(ObjectContext context, Pkg pkg) {
        Preconditions.checkArgument(null!=context, "an object context is required");
        Preconditions.checkArgument(null!=pkg, "a package is required");
        cache.invalidate(pkg.getName());
    }

    private synchronized byte[] getGenericHvif() {
        if(null==genericHvif) {
            try (InputStream inputStream = RenderedPkgIconRepositoryImpl.class.getResourceAsStream("/img/generic.hvif")) {
                genericHvif = ByteStreams.toByteArray(inputStream);
            }
            catch(IOException ioe) {
                throw new IllegalStateException("unable to load the generic HVIF data.", ioe);
            }
        }

        return genericHvif;
    }

    @Override
    public byte[] renderGeneric(int size) {
        Preconditions.checkArgument(size <= SIZE_MAX && size >= SIZE_MIN, "bad size");

        try {
            return genericCache.get(
                    size,
                    () -> pngOptimizationService.optimize(hvifRenderingService.render(size, getGenericHvif())));
        }
        catch(Exception e) {
            throw new IllegalStateException("unable to render a generic image", e);
        }
    }

    @Override
    public Optional<byte[]> render(
            int size,
            ObjectContext context,
            Pkg pkg) {

        Preconditions.checkArgument(size <= SIZE_MAX && size >= SIZE_MIN, "bad size");
        Preconditions.checkArgument(null != context, "an object context is required");
        Preconditions.checkArgument(null != pkg, "a package is required");

        Cache<Integer, Optional<byte[]>> pkgCache = getOrCreatePkgCache(pkg.getName());

        try {
            return pkgCache.get(size, () -> {

                // first look for the HVIF icon and render the icon from that.

                {
                    MediaType hvifMediaType = MediaType.getByCode(context, MediaType.MEDIATYPE_HAIKUVECTORICONFILE).get();
                    Optional<PkgIcon> hvifPkgIconOptional = pkg.getPkgIcon(hvifMediaType, null);

                    if (hvifPkgIconOptional.isPresent()) {
                        byte[] hvifData = hvifPkgIconOptional.get().getPkgIconImage().get().getData();
                        byte[] pngData = pngOptimizationService.optimize(hvifRenderingService.render(size, hvifData));
                        return Optional.of(pngData);
                    }
                }

                // If there is no HVIF then it is possible to fall back to PNG images.

                {
                    List<PkgIcon> pkgIconList = pkg.getPkgIcons()
                            .stream()
                            .filter(pi -> pi.getMediaType().getCode().equals(com.google.common.net.MediaType.PNG.toString()))
                            .sorted((pi1, pi2) -> pi1.getSize().compareTo(pi2.getSize()))
                            .collect(Collectors.toList());

                    for(PkgIcon pkgIcon : pkgIconList) {
                        if(pkgIcon.getSize() >= size) {
                            return Optional.of(pkgIcon.getPkgIconImage().get().getData());
                        }
                    }

                    if(!pkgIconList.isEmpty()) {
                        return Optional.of(pkgIconList.get(pkgIconList.size()-1).getPkgIconImage().get().getData());
                    }

                }

                return Optional.empty();
            });
        }
        catch(Exception e) {
            throw new RuntimeException("unable to get the rendered package icon for; " + pkg.getName(), e);
        }

    }
}
