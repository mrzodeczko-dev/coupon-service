package com.rzodeczko.application.port.input;

import com.rzodeczko.domain.model.Coupon;

public interface CreateCouponUseCase {

    Coupon create(CreateCouponCommand command);
}
