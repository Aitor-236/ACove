package com.acove.blog.article.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.acove.blog.article.dto.ArticleDTO;
import com.acove.blog.article.dto.ArticleDeleteResult;
import com.acove.blog.article.dto.ArticleDetailVO;
import com.acove.blog.article.dto.ArticleVO;
import com.acove.blog.article.entity.Article;
import com.acove.blog.article.entity.ArticleCategory;
import com.acove.blog.article.entity.ArticleTag;
import com.acove.blog.article.entity.Tag;
import com.acove.blog.article.mapper.AdminArticleMapper;
import com.acove.blog.article.mapper.ArticleCategoryMapper;
import com.acove.blog.article.mapper.ArticleTagMapper;
import com.acove.blog.article.mapper.TagMapper;
import com.acove.blog.article.service.AdminArticleService;
import com.acove.blog.common.exception.BusinessException;
import com.acove.blog.common.utils.PageParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor
public class AdminArticleServiceImpl implements AdminArticleService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PUBLISHED = "published";

    /** tag.name 的列长度，超长直接给 400，避免数据库截断异常变成 500。 */
    private static final int TAG_NAME_MAX_LENGTH = 50;

    private final AdminArticleMapper adminArticleMapper;
    private final ArticleCategoryMapper articleCategoryMapper;
    private final ArticleTagMapper articleTagMapper;
    private final TagMapper tagMapper;
    private final ArticleVOAssembler articleVOAssembler;

    @Override
    public Page<ArticleVO> listAll(long page, long size, String category, String keyword, String status) {
        size = PageParam.requireValid(page, size);

        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .orderByDesc(Article::getUpdatedAt);

        // 状态筛选只认 draft / published，其它值按"不筛选"处理，避免前端传空串时报错。
        if (StringUtils.hasText(status)) {
            String normalizedStatus = status.trim().toLowerCase();
            if (STATUS_DRAFT.equals(normalizedStatus) || STATUS_PUBLISHED.equals(normalizedStatus)) {
                wrapper.eq(Article::getStatus, normalizedStatus);
            }
        }

        if (StringUtils.hasText(category)) {
            ArticleCategory matched = resolveCategory(category);
            if (matched == null) {
                return articleVOAssembler.emptyPage(page, size);
            }
            wrapper.eq(Article::getCategoryId, matched.getId());
        }

        if (StringUtils.hasText(keyword)) {
            String likeKeyword = keyword.trim();
            wrapper.and(w -> w.like(Article::getTitle, likeKeyword)
                    .or()
                    .like(Article::getSummary, likeKeyword));
        }

        Page<Article> articlePage = adminArticleMapper.selectPage(new Page<>(page, size), wrapper);
        return articleVOAssembler.toVoPage(articlePage);
    }

    @Override
    @Transactional
    public ArticleVO createArticle(ArticleDTO articleDTO, Long authorId) {
        if (articleDTO == null || !StringUtils.hasText(articleDTO.getTitle())) {
            throw new BusinessException("文章标题不能为空");
        }
        if (authorId == null) {
            throw new BusinessException("无法获取当前登录用户，请重新登录");
        }

        ArticleCategory category = resolveCategory(articleDTO.getCategoryName());
        if (category == null) {
            throw new BusinessException("文章分类不存在");
        }

        Article article = new Article();
        article.setTitle(articleDTO.getTitle().trim());
        article.setSummary(articleDTO.getSummary());
        article.setContentMarkdown(articleDTO.getContent());
        article.setCategoryId(category.getId());
        // 作者只认登录态里的用户ID，前端传什么都不影响这里。
        article.setAuthorId(authorId);
        article.setStatus(STATUS_DRAFT);

        LocalDateTime now = LocalDateTime.now();
        article.setCreatedAt(now);
        article.setUpdatedAt(now);

        adminArticleMapper.insert(article);
        // 新建时按编辑器提交的标签建关联，没传标签就当作没有标签
        syncArticleTags(article.getId(), articleDTO.getTags());

        return loadArticleVO(article.getId());
    }

    /**
     * 根据分类名称（或英文标识）查询分类，未提供分类名称时返回 null。
     */
    private ArticleCategory resolveCategory(String categoryName) {
        if (!StringUtils.hasText(categoryName)) {
            return null;
        }
        String keyword = categoryName.trim();
        ArticleCategory category = articleCategoryMapper.selectOne(
                new LambdaQueryWrapper<ArticleCategory>()
                        .eq(ArticleCategory::getName, keyword)
                        .last("limit 1"));
        if (category == null) {
            category = articleCategoryMapper.selectOne(
                    new LambdaQueryWrapper<ArticleCategory>()
                            .eq(ArticleCategory::getSlug, keyword)
                            .last("limit 1"));
        }
        return category;
    }

    @Override
    @Transactional
    public ArticleVO updateArticle(ArticleDTO articleDTO) {
        if (articleDTO == null || articleDTO.getId() == null) {
            throw new BusinessException("文章ID不能为空");
        }

        Article existing = adminArticleMapper.selectById(articleDTO.getId());
        if (existing == null) {
            throw new BusinessException(404, "文章不存在");
        }

        Article article = new Article();
        article.setId(existing.getId());
        if (StringUtils.hasText(articleDTO.getTitle())) {
            article.setTitle(articleDTO.getTitle().trim());
        }
        if (articleDTO.getSummary() != null) {
            article.setSummary(articleDTO.getSummary());
        }
        if (articleDTO.getContent() != null) {
            article.setContentMarkdown(articleDTO.getContent());
        }
        if (StringUtils.hasText(articleDTO.getCategoryName())) {
            ArticleCategory category = resolveCategory(articleDTO.getCategoryName());
            if (category == null) {
                throw new BusinessException("文章分类不存在");
            }
            article.setCategoryId(category.getId());
        }
        article.setUpdatedAt(LocalDateTime.now());

        adminArticleMapper.updateById(article);
        // 标签为 null 表示这次不改标签，传了（哪怕是空列表）就整体覆盖
        if (articleDTO.getTags() != null) {
            syncArticleTags(existing.getId(), articleDTO.getTags());
        }
        return loadArticleVO(existing.getId());
    }

    @Override
    public ArticleDetailVO getArticleDetail(Long id) {
        if (id == null) {
            throw new BusinessException("文章ID不能为空");
        }

        Article article = adminArticleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        return new ArticleDetailVO(article, loadCategory(article.getCategoryId()),
                articleTagMapper.selectTagNamesByArticleId(id));
    }

    @Override
    public ArticleVO publishArticle(Long id) {
        if (id == null) {
            throw new BusinessException("文章ID不能为空");
        }

        Article existing = adminArticleMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "文章不存在");
        }
        // 已经发布过的不重复动作，避免刷新发布时间把文章顶到前台列表最前面。
        if (STATUS_PUBLISHED.equals(existing.getStatus())) {
            return loadArticleVO(id);
        }
        // article 表的标题、摘要、正文都是 NOT NULL，缺内容就发不出完整文章。
        if (!StringUtils.hasText(existing.getTitle())
                || !StringUtils.hasText(existing.getSummary())
                || !StringUtils.hasText(existing.getContentMarkdown())) {
            throw new BusinessException("文章标题、摘要和正文都不为空才能发布");
        }

        LocalDateTime now = LocalDateTime.now();
        Article article = new Article();
        article.setId(existing.getId());
        article.setStatus(STATUS_PUBLISHED);
        article.setPublishedAt(now);
        article.setUpdatedAt(now);
        adminArticleMapper.updateById(article);

        return loadArticleVO(id);
    }

    @Override
    public ArticleVO unpublishArticle(Long id) {
        if (id == null) {
            throw new BusinessException("文章ID不能为空");
        }

        Article existing = adminArticleMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "文章不存在");
        }
        // 已经是草稿的不重复动作，避免刷新更新时间把草稿顶到列表最前面。
        if (STATUS_DRAFT.equals(existing.getStatus())) {
            return loadArticleVO(id);
        }

        // 草稿不应该带发布时间，这里显式把 published_at 置空，重新发布时再写入新时间。
        markAsDraft(existing.getId());

        return loadArticleVO(id);
    }

    @Override
    public ArticleDeleteResult deleteArticle(Long id) {
        if (id == null) {
            throw new BusinessException("文章ID不能为空");
        }

        Article existing = adminArticleMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "文章不存在");
        }

        // 已发布的文章不允许物理删除，先取消发布回退为草稿。
        if (STATUS_PUBLISHED.equals(existing.getStatus())) {
            markAsDraft(existing.getId());
            return ArticleDeleteResult.UNPUBLISHED;
        }

        // 草稿状态直接删除，关联的 article_tag 由外键级联清理。
        adminArticleMapper.deleteById(id);
        return ArticleDeleteResult.DELETED;
    }

    @Override
    public void forceDeleteArticle(Long id) {
        if (id == null) {
            throw new BusinessException("文章ID不能为空");
        }

        Article existing = adminArticleMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "文章不存在");
        }
        // 物理删除只对草稿开放，已发布的必须先取消发布，避免误删线上文章。
        if (!STATUS_DRAFT.equals(existing.getStatus())) {
            throw new BusinessException("只有草稿可以彻底删除，已发布的文章请先取消发布");
        }

        // 关联的 article_tag 由外键 ON DELETE CASCADE 清理。
        adminArticleMapper.deleteById(id);
    }

    /**
     * 把文章回退为草稿：状态改回 draft，同时清空 published_at
     * （实体更新默认忽略 null 字段，所以这里用 UpdateWrapper 显式写 NULL）。
     */
    private void markAsDraft(Long id) {
        adminArticleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, id)
                .set(Article::getStatus, STATUS_DRAFT)
                .set(Article::getPublishedAt, null)
                .set(Article::getUpdatedAt, LocalDateTime.now()));
    }

    /**
     * 按ID读取文章并转换为展示对象，文章不存在时返回 null。
     */
    private ArticleVO loadArticleVO(Long id) {
        Article article = adminArticleMapper.selectById(id);
        if (article == null) {
            return null;
        }
        ArticleCategory category = loadCategory(article.getCategoryId());
        ArticleVO vo = ArticleVO.from(article, category == null ? null : category.getName());
        vo.setTags(articleTagMapper.selectTagNamesByArticleId(id));
        return vo;
    }

    /**
     * 覆盖式写入文章标签：先清掉旧关联，再按名称匹配已有标签，
     * 匹配不到的（编辑器里新输入的标签）顺手建一个，最后补齐关联行。
     */
    private void syncArticleTags(Long articleId, List<String> tagNames) {
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>()
                .eq(ArticleTag::getArticleId, articleId));

        List<String> normalized = normalizeTagNames(tagNames);
        if (normalized.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (String name : normalized) {
            Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getName, name)
                    .last("limit 1"));
            if (tag == null) {
                tag = new Tag();
                tag.setName(name);
                tag.setCreatedAt(now);
                tagMapper.insert(tag);
            }

            ArticleTag relation = new ArticleTag();
            relation.setArticleId(articleId);
            relation.setTagId(tag.getId());
            articleTagMapper.insert(relation);
        }
    }

    /** 去空白、去重、校验长度；重复的标签只留一个，否则复合主键会冲突。 */
    private List<String> normalizeTagNames(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (String raw : tagNames) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String name = raw.trim();
            if (name.length() > TAG_NAME_MAX_LENGTH) {
                throw new BusinessException("标签不能超过 " + TAG_NAME_MAX_LENGTH + " 个字符");
            }
            if (!normalized.contains(name)) {
                normalized.add(name);
            }
        }
        return normalized;
    }

    /**
     * 按ID读取分类，分类ID为空或分类已被删除时返回 null。
     */
    private ArticleCategory loadCategory(Long categoryId) {
        return categoryId == null ? null : articleCategoryMapper.selectById(categoryId);
    }
}
