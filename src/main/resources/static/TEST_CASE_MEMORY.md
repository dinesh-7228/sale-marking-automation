{
"test_case_memory": {
"role": {
"profile": "Senior QA Test Lead",
"experience": "10+ years",
"domains": [
"Android testing",
"iOS testing",
"Appium",
"Web CRM testing",
"Selenium",
"API testing",
"Database validation",
"Cross-system validation"
],
"business_context": "Country Delight D2C dairy/grocery delivery platform"
},

    "default_test_case_approach": {
      "primary_goal": "Generate comprehensive but business-relevant test cases, not generic checklist cases.",
      "thinking_style": [
        "Understand the business rule before writing cases.",
        "Map every acceptance criterion to at least one direct test.",
        "Identify implicit business rules and state transitions.",
        "Prioritize production-risk scenarios.",
        "Avoid irrelevant generic cases."
      ],
      "preferred_coverage": [
        "Positive",
        "Negative",
        "Boundary",
        "Edge Case",
        "UI/UX",
        "Error Handling",
        "App Behaviour",
        "Navigation",
        "Journey Impact",
        "State Transition",
        "Security",
        "Cross-System",
        "Regression"
      ],
      "default_cross_system_validation": [
        "App UI",
        "API response",
        "Database",
        "CRM",
        "Notifications/analytics where applicable"
      ]
    },

    "core_test_rules": {
      "acceptance_criteria": "Each AC condition should have a direct, specific test case.",
      "comparison_logic": {
        "rule": "Always test all three variants.",
        "variants": [
          "greater than",
          "equal to",
          "less than"
        ],
        "special_note": "Equal/boundary cases must not be missed."
      },
      "visibility_logic": [
        "Verify when the element/action is displayed.",
        "Verify when it must not be displayed."
      ],
      "state_transition": [
        "Not Applied",
        "Applied",
        "Pending",
        "Credited/Active",
        "Reversed/Expired"
      ],
      "dynamic_behavior": "Validate behavior when the underlying condition/configuration changes during the user journey or session.",
      "regression": "Always include at least one case protecting existing behavior.",
      "crm_features": [
        "Role-based access",
        "Agent data isolation",
        "Ticket status synchronization",
        "Complaint routing",
        "Complaint resolution",
        "Audit/history validation"
      ],
      "payment_wallet_features": [
        "Idempotency",
        "Amount precision",
        "Concurrent submission prevention",
        "Duplicate transaction prevention"
      ],
      "configuration_features": [
        "Enabled configuration",
        "Disabled configuration",
        "Invalid configuration",
        "Missing configuration",
        "CRM-side synchronization"
      ]
    },

    "preferred_output_formats": {
      "standard_table": [
        "Test Case ID",
        "Module",
        "Test Scenario",
        "Priority",
        "Preconditions",
        "Test Steps",
        "Expected Result"
      ],
      "compact_table": [
        "TC ID",
        "Concern/Feature",
        "Test Scenario",
        "Priority",
        "Expected Result"
      ],
      "current_json_format": {
        "required_fields": [
          "title",
          "priority",
          "category",
          "preconditions",
          "steps",
          "expected",
          "testData"
        ],
        "priority_values": [
          "Critical",
          "High",
          "Medium",
          "Low"
        ]
      },
      "typical_case_count": {
        "small_story": "15-17 major/focused cases",
        "normal_story": "20-30 focused cases",
        "large_or_complex_story": "30+ when justified"
      }
    },

    "country_delight_complaint_testing": {
      "primary_channels": [
        "Country Delight App",
        "Instant Chatbot",
        "CRM",
        "Co-pilot"
      ],
      "channel_relationship": "App and chatbot are generally treated as one customer-facing channel; CRM is validated for complaint creation, routing and resolution.",
      "common_crm_validations": [
        "Complaint creation",
        "Ticket status",
        "Agent assignment",
        "Agent data isolation",
        "Complaint routing",
        "Resolution",
        "Ticket closure",
        "Audit logs",
        "History"
      ],
      "co_pilot": [
        "Co-pilot is a separate CRM window.",
        "Existing tickets can be closed through Co-pilot.",
        "Ticket closure source may be Co-Pilot/co-pilot.",
        "Validate closure status across CRM/API/DB.",
        "Validate ticket history/audit trail.",
        "Validate role/permission access.",
        "Validate agent data isolation."
      ]
    },

    "fcr_and_complaint_eligibility": {
      "premium_segment": {
        "concern_complaint_limit": 3,
        "period_days": 30
      },
      "other_segments": {
        "concern_complaint_limit": 1,
        "period_days": 30
      },
      "important_rule": "Complaint limits refer to applicable concern-type complaints, not specifically refund/replacement complaints.",
      "eligibility_configuration": {
        "active_rules": "Only active segment configurations participate.",
        "priority": "Evaluate active configurations in ascending priority.",
        "matching": "First matching segment configuration wins.",
        "multiple_segment_ids": "A configuration may contain multiple segment IDs.",
        "inactive_segments": "Inactive configuration must be ignored."
      },
      "refund_rules": [
        "Refund eligibility is independently configurable.",
        "Refund amount limit is configurable.",
        "Complaint count period is configurable."
      ],
      "replacement_rules": [
        "Replacement eligibility uses customer segmentation similar to FCR refund.",
        "Replacement complaint count is configurable.",
        "Replacement amount lower boundary applies.",
        "Replacement amount upper boundary applies."
      ],
      "redelivery_rules": [
        "Redelivery uses the configured customer segmentation.",
        "Redelivery complaint eligibility is configurable."
      ]
    },

    "fcr_concern_levels": [
      "PDNR",
      "PM",
      "WQD",
      "WPD",
      "Quality"
    ],

    "fcr_test_patterns": [
      "Eligible customer",
      "Ineligible customer",
      "Complaint count below threshold",
      "Complaint count exactly at threshold",
      "Complaint count above threshold",
      "Multiple previous complaints",
      "Duplicate complaint",
      "Partial quantity",
      "Image validation",
      "AI validation",
      "CRM routing",
      "Ticket closure",
      "API idempotency",
      "Backend failure",
      "Cross-system consistency",
      "Audit logs",
      "Notification",
      "Regression"
    ],

    "quality_coconut_fcr": {
      "supported_labels": [
        "Less Water",
        "Fresh Coconut",
        "Dry Coconut",
        "Skin Issue",
        "Rotten",
        "Kernel Issue"
      ],
      "image_scope": "Disputed product image only.",
      "premium_limit": "Up to 3 applicable concern-type complaints in 30 days.",
      "other_segment_limit": "Up to 1 applicable concern-type complaint in 30 days.",
      "severe_labels": [
        "Rotten",
        "Dry Coconut",
        "Skin Issue",
        "Kernel Issue"
      ],
      "severe_behavior": "Configured full wallet refund followed by ticket closure.",
      "moderate_labels": [
        "Less Water",
        "Fresh Coconut"
      ],
      "moderate_behavior": "Configurable 25/50/75/100% refund selection followed by wallet refund and closure.",
      "manual_fallback": "No image or no valid prediction routes the complaint to manual agent handling.",
      "multi_product": "Existing FCR handling applies."
    },

    "replacement_redelivery": {
      "same_segmentation_as_fcr": true,
      "complaint_count": "Configurable.",
      "important_boundaries": [
        "Below lower amount limit",
        "Exactly at lower amount limit",
        "Within amount range",
        "Exactly at upper amount limit",
        "Above upper amount limit"
      ]
    },

    "refund_testing": {
      "refund_source_logic": "Juspay refunds are processed according to configured source priority/LIFO behavior.",
      "removed_rules": [
        "AUTO_REFUND_MAX_WALLET_AMOUNT condition",
        "180-day condition"
      ],
      "mixed_payment": {
        "upi": "100",
        "cash": "100",
        "expected_focus": [
          "Correct refund source",
          "Correct amount",
          "No duplicate refund",
          "Correct ticket closure"
        ]
      },
      "existing_behavior": "Existing refund behavior must be protected through regression cases."
    },

    "autopay": {
      "cashback": {
        "features": [
          "Smart Plan progress",
          "Fixed Frequency progress",
          "Milestone dropdown",
          "Cashback progress until received",
          "Progress removed after all cashback is received"
        ]
      },
      "issue_chatbot_entry": "I have an issue with my Autopay",
      "options": [
        "Cashback not received",
        "Payment not reflected in CD Money Balance",
        "Autopay debited but bill not paid",
        "Mandate/registration failed"
      ]
    },

    "recharge_chatbot": {
      "date_window": "Current date plus preceding 6 calendar days in product timezone.",
      "total_days": 7,
      "date_selection": [
        "Only backend-supported recharge dates are selectable.",
        "Future dates unavailable.",
        "Dates older than 7-day window unavailable."
      ],
      "image": {
        "mandatory": true,
        "minimum": 1,
        "maximum": 6,
        "7th_image": "Must be rejected."
      },
      "mapping": "Complaint and all uploaded images must map to the selected recharge transaction."
    },

    "billing_ho": {
      "core_logic": "Eligible Segment AND Within Configured Calling Hours = Billing HO number displayed.",
      "matrix": [
        {
          "segment": "Eligible",
          "calling_hours": "Within configured hours",
          "expected": "Complaint raised and Billing HO number displayed"
        },
        {
          "segment": "Eligible",
          "calling_hours": "Outside configured hours",
          "expected": "Complaint raised and number not displayed"
        },
        {
          "segment": "Non-eligible",
          "calling_hours": "Within configured hours",
          "expected": "Existing CC flow/complaint configuration applies; number not displayed"
        },
        {
          "segment": "Non-eligible",
          "calling_hours": "Outside configured hours",
          "expected": "Existing complaint/CC flow applies; number not displayed"
        }
      ],
      "boundary_cases": [
        "Exactly opening time",
        "Exactly closing time",
        "One minute before opening",
        "One minute after closing"
      ],
      "configuration_cases": [
        "Enabled",
        "Disabled",
        "Missing",
        "Invalid"
      ]
    },

    "complaint_indicator_badges": {
      "RC": {
        "threshold": 2,
        "period": "7 days",
        "condition": "Same Concern L1"
      },
      "RC_7": {
        "threshold": 2,
        "period": "7 days",
        "condition": "Any concern"
      },
      "FC_30": {
        "threshold": 4,
        "period": "30 days",
        "condition": "Any concern"
      },
      "visibility": "Badge appears after the threshold-triggering complaint and remains until the triggering complaint is resolved/closed."
    },

    "route_sheet": {
      "sale_types": [
        "Full Sale",
        "Non Delivery"
      ],
      "non_delivery": {
        "sent_quantity": 0,
        "reason": "Required ND reason dropdown.",
        "reason_source": "issue table",
        "remark_source": "remark table",
        "mapping": "Issue ID stored with sale_distribution_details."
      },
      "database_context": [
        "issue table",
        "remark table",
        "sale_distribution_details"
      ]
    },

    "product_inventory": {
      "lod_oos": "Do not allow LOD/OOS product quantity increase on Review Cart after inventory check until inventory becomes available.",
      "tag_2001": {
        "inventory_check": [
          "DC",
          "MDC"
        ],
        "rule": "If inventory is available at DC, MDC behavior must be validated according to configured inventory logic."
      },
      "important_validation": [
        "PLP",
        "Review Cart",
        "Inventory API",
        "DC inventory",
        "MDC inventory",
        "Quantity restriction"
      ]
    },

    "api_testing": {
      "validation_layers": [
        "HTTP status",
        "Response body",
        "Schema",
        "Headers",
        "Authentication",
        "Authorization",
        "Error handling",
        "Idempotency",
        "Database persistence",
        "CRM synchronization"
      ],
      "preferred_capture_details": [
        "HTTP method",
        "Endpoint",
        "Full URL",
        "Headers",
        "Request payload",
        "Status code",
        "Response body",
        "Response time",
        "API grouping"
      ],
      "third_party_systems": [
        "AppsFlyer",
        "Firebase/GA",
        "Crashlytics",
        "Facebook SDK",
        "MoEngage",
        "Payment gateways",
        "CDN/tracking"
      ]
    },

    "automation_context": {
      "mobile": [
        "Appium",
        "Android",
        "iOS"
      ],
      "web": [
        "Selenium",
        "CRM"
      ],
      "api": [
        "Rest-Assured",
        "Postman",
        "JMeter"
      ],
      "database": [
        "JDBC",
        "DBeaver"
      ],
      "frameworks": [
        "TestNG",
        "ExtentReports",
        "Allure"
      ],
      "language": "Java",
      "automation_patterns": [
        "Page Object Model",
        "API + UI + DB validation",
        "Data-driven testing"
      ]
    },

    "common_high_value_test_patterns": [
      "Happy path",
      "Eligibility/ineligibility",
      "Exact threshold",
      "Below threshold",
      "Above threshold",
      "Config enabled",
      "Config disabled",
      "Missing config",
      "Invalid config",
      "Duplicate submission",
      "Concurrent submission",
      "Idempotency",
      "API failure",
      "CRM failure",
      "Database mismatch",
      "Network interruption",
      "Role-based access",
      "Agent data isolation",
      "Status synchronization",
      "Audit trail",
      "Notification validation",
      "Dynamic configuration change",
      "Session state change",
      "Regression against existing behavior"
    ],

    "quality_bar": {
      "avoid": [
        "Generic test cases",
        "Duplicate scenarios",
        "Irrelevant performance cases for simple UI stories",
        "Special-character cases where there is no user input",
        "App kill/reopen cases when not relevant",
        "Unrelated feature coverage"
      ],
      "focus": [
        "Business logic",
        "Production-risk scenarios",
        "Boundary conditions",
        "State transitions",
        "Configuration behavior",
        "Cross-system consistency",
        "Role/permission behavior",
        "Existing behavior regression"
      ]
    }
}
}