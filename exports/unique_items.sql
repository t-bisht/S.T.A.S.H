-- Unique items from online_retail_purchase_history.
-- One row per distinct item_name. When the same item_name appears with
-- different sub_category_id, DISTINCT ON keeps exactly one (lowest sub_category_id
-- via the ORDER BY). category/subcategory names resolved by FK join to
-- item_categories / item_subcategories (05_item_categories.sql).
--
-- EXCLUDES rows whose item_name carries a leading quantity prefix ("N x Name")
-- -- LLM parse noise on order 250605518928230 (rows id 274-279); quantity is
-- already its own column, so these names are dirty duplicates.
SELECT DISTINCT ON (p.item_name)
       p.item_name,
       s.subcategory_name,
       c.category_name
FROM   online_retail_purchase_history p
JOIN   item_subcategories s ON s.subcategory_id = p.sub_category_id
JOIN   item_categories    c ON c.category_id    = s.category_id
WHERE  p.item_name !~ '^[0-9]+ x '
ORDER  BY p.item_name, p.sub_category_id;
