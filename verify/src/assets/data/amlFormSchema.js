/**
 * Schéma AML dotazníku — české fyzické osoby / OSVČ.
 *
 * Vlastnosti pole:
 *   key            – klíč ve formData
 *   type           – 'text' | 'select' | 'textarea' | 'boolean'
 *   required       – musí být vyplněno, pokud je pole viditelné
 *   optional       – vykreslí se jen pokud je klíč v config.optionalFields
 *   options        – klíče možností pro select (překlad přes step.aml.options.<key>.<opt>)
 *   optionsSource  – 'countries' → načítá z countries.json místo options[]
 *   tKey           – přepíše klíč překladu (výchozí je key)
 *   showWhen       – pole podmínek { field, eq } nebo { field, in: [...] }
 *   showWhenMode   – 'any' (OR) | 'all' (AND, výchozí)
 */

export const AML_FORM_SCHEMA = [
  {
    section: 'employment',
    fields: [
      {
        key: 'employment_status',
        type: 'select',
        required: true,
        options: ['employed', 'self_employed', 'unemployed', 'retired', 'student', 'other'],
      },
      {
        key: 'employer_name',
        type: 'text',
        required: true,
        showWhen: [{ field: 'employment_status', eq: 'employed' }],
      },
      {
        key: 'job_title',
        type: 'text',
        required: true,
        showWhen: [{ field: 'employment_status', eq: 'employed' }],
      },
      {
        key: 'source_of_income',
        type: 'select',
        required: true,
        options: [
          'employment',
          'self_employment',
          'pension',
          'student_support',
          'social_benefits',
          'investments',
          'rental_income',
          'other',
        ],
      },
      {
        key: 'monthly_income_range',
        type: 'select',
        required: true,
        // Intervaly jsou v CZK
        options: [
          'under_20000',
          'range_20000_50000',
          'range_50000_100000',
          'range_100000_250000',
          'over_250000',
        ],
      },
      {
        key: 'industry',
        type: 'select',
        required: false,
        optional: true,
        // Relevantní jen pro osoby, které skutečně pracují
        showWhen: [{ field: 'employment_status', in: ['employed', 'self_employed'] }],
        options: [
          'finance',
          'healthcare',
          'technology',
          'retail',
          'construction',
          'education',
          'government',
          'media',
          'manufacturing',
          'other',
        ],
      },
    ],
  },
  {
    section: 'funds',
    fields: [
      {
        key: 'source_of_funds',
        type: 'select',
        required: true,
        // Detailnější než source_of_income - konkrétní původ vkládaných prostředků
        options: [
          'salary',
          'business_income',
          'savings',
          'inheritance',
          'gift',
          'sale_of_assets',
          'investment_proceeds',
          'other',
        ],
      },
      {
        key: 'source_of_wealth',
        type: 'textarea',
        required: false,
        optional: true,
      },
    ],
  },
  {
    section: 'account',
    fields: [
      /* {
        key: 'purpose_of_account',
        type: 'select',
        required: true,
        optionsSource: 'config',
      }, */
      {
        key: 'expected_monthly_volume',
        type: 'select',
        required: true,
        // Intervaly v CZK
        options: [
          'under_5000',
          'range_5000_25000',
          'range_25000_100000',
          'range_100000_500000',
          'over_500000',
        ],
      },
      {
        key: 'expected_transaction_count',
        type: 'select',
        required: true,
        options: ['below_5', 'range_5_20', 'range_20_50', 'above_50'],
      },
      {
        key: 'foreign_transactions_expected',
        type: 'boolean',
        required: false,
        optional: true,
      },
    ],
  },
  {
    section: 'tax',
    fields: [
      {
        key: 'is_czech_tax_resident',
        type: 'boolean',
        required: true,
      },
      {
        // Zobrazuje se jen když uživatel NENÍ český daňový rezident
        key: 'tax_residency_country',
        type: 'select',
        required: true,
        optionsSource: 'countries', // načítá se z countries.json
        showWhen: [{ field: 'is_czech_tax_resident', eq: false }],
      },
      {
        key: 'tin',
        type: 'text',
        required: true,
        optional: true,
        showWhen: [{ field: 'is_czech_tax_resident', eq: false }],
      },
    ],
  },
  {
    section: 'compliance',
    fields: [
      {
        key: 'pep_self_declared',
        type: 'boolean',
        required: true,
        tKey: 'pep_self',
      },
      {
        key: 'close_associate_of_pep',
        type: 'boolean',
        required: true,
        tKey: 'close_pep',
      },
      {
        key: 'politically_exposed_person_explanation',
        type: 'textarea',
        required: true,
        tKey: 'pep_explanation',
        showWhen: [
          { field: 'pep_self_declared', eq: true },
          { field: 'close_associate_of_pep', eq: true },
        ],
        showWhenMode: 'any',
      },
      {
        key: 'acting_on_own_behalf',
        type: 'boolean',
        required: false,
        optional: true,
      },
      {
        key: 'crypto_activity_declaration',
        type: 'boolean',
        required: false,
        optional: true,
        tKey: 'crypto',
      },
    ],
  },
]
