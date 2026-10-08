package com.acove.blog.article.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.acove.blog.article.dto.TagVO;
import com.acove.blog.article.mapper.TagMapper;
import com.acove.blog.article.service.TagService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;

    @Override
    public List<TagVO> listPublishedTags() {
        List<TagVO> tags = new ArrayList<>();
        for (Map<String, Object> row : tagMapper.selectPublishedTagCounts()) {
            Object tagId = row.get("tag_id");
            Object name = row.get("name");
            if (!(tagId instanceof Number) || name == null) {
                continue;
            }

            TagVO vo = new TagVO();
            vo.setId(((Number) tagId).longValue());
            vo.setName(String.valueOf(name));
            Object articleCount = row.get("article_count");
            vo.setArticleCount(articleCount instanceof Number ? ((Number) articleCount).longValue() : 0L);
            tags.add(vo);
        }
        return tags;
    }
}
