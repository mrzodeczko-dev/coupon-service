package com.rzodeczko.application.port.input;

import com.rzodeczko.domain.model.Coupon;

public interface UseCouponUseCase {

    Coupon use(UseCouponCommand command);
}
