package com.acove.blog.gallery.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.acove.blog.gallery.entity.GalleryImage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
public interface GalleryImageMapper extends BaseMapper<GalleryImage> {

    @Select("SELECT * FROM gallery_image WHERE id = #{id} FOR UPDATE")
    GalleryImage selectByIdForUpdate(@Param("id") Long id);
}
