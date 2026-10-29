package com.company.project.hotel.dto;

import com.company.project.hotel.model.HotelPolicyType;

/** One policy paragraph: children, extra beds, pets, smoking, ID and deposit, payment methods or other. */
public record HotelPolicyItem(HotelPolicyType type, String description) {
}
