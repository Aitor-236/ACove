package com.acove.blog.common.utils;

import com.acove.blog.common.exception.BusinessException;

/**
 * 分页参数校验。size=0 时 MyBatis-Plus 会算出 total 却返回空 records，
 * page=0/负数 的偏移量也没有意义，所以统一在 service 入口挡住。
 */
public final class PageParam {

    /** 每页条数上限，超过按上限处理，避免一次查回过多数据。 */
    public static final long MAX_PAGE_SIZE = 100;

    private PageParam() {
    }

    /**
     * 校验页码和每页条数，不合法时抛业务异常（400）；
     * 每页条数超过 {@link #MAX_PAGE_SIZE} 时按上限处理，不报错。
     *
     * @param page 页码，从 1 开始
     * @param size 请求的每页条数，至少 1
     * @return 实际生效的每页条数，调用方应当用它做分页查询
     */
    public static long requireValid(long page, long size) {
        if (page < 1 || size < 1) {
            throw new BusinessException("分页参数不合法：page 和 size 都必须大于 0");
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
