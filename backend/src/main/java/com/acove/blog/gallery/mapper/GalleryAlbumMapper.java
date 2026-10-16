package com.acove.blog.gallery.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.acove.blog.gallery.dto.GalleryAlbumVO;
import com.acove.blog.gallery.entity.GalleryAlbum;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface GalleryAlbumMapper extends BaseMapper<GalleryAlbum> {

    @Select("""
            SELECT a.id, a.name, a.description,
                   (SELECT i.image_url
                      FROM gallery_image i
                     WHERE i.gallery_id = a.id
                     ORDER BY i.sort_order ASC, i.id ASC
                     LIMIT 1) AS coverImage,
                   (SELECT COUNT(*) FROM gallery_image i WHERE i.gallery_id = a.id) AS imageCount,
                   a.created_at AS createdAt, a.updated_at AS updatedAt
              FROM gallery_album a
             ORDER BY a.created_at DESC, a.id DESC
            """)
    List<GalleryAlbumVO> selectGallerySummaries();

    @Select("""
            SELECT a.id, a.name, a.description,
                   (SELECT i.image_url
                      FROM gallery_image i
                     WHERE i.gallery_id = a.id
                     ORDER BY i.sort_order ASC, i.id ASC
                     LIMIT 1) AS coverImage,
                   (SELECT COUNT(*) FROM gallery_image i WHERE i.gallery_id = a.id) AS imageCount,
                   a.created_at AS createdAt, a.updated_at AS updatedAt
              FROM gallery_album a
             WHERE a.id = #{id}
            """)
    GalleryAlbumVO selectGallerySummary(@Param("id") Long id);

    /** 跨请求、跨实例串行化同一图库的写入。 */
    @Select("SELECT * FROM gallery_album WHERE id = #{id} FOR UPDATE")
    GalleryAlbum selectByIdForUpdate(@Param("id") Long id);
}
