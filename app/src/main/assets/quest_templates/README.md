# Quest Template CSV

Each CSV file in this folder is loaded as shareable quest templates.

Columns:

template_name,title,description,type,gp_reward,exp_reward,target_count,difficulty,tags,min_rank,active_weekdays,has_time_limit,penalty_gp,penalty_exp,bonus_gp,bonus_exp,grace_period_days,submission_deadline_days,weekly_refresh_weekday,monthly_refresh_day,pinned

Notes:
- `type` uses enum names, such as `DAILY_QUEST`, `WEEKLY_QUEST`, `MONTHLY_QUEST`.
- `difficulty` uses `EASY`, `NORMAL`, `HARD`, `LEGENDARY`.
- `min_rank` uses `F`, `E`, `D`, `C`, `B`, `A`, `S`.
- `tags` and `active_weekdays` use semicolons, for example `健康;喝水` and `1;2;3;4;5`.
- Weekdays are `1=Mon` through `7=Sun`.
- `DAILY_QUEST`, `WEEKLY_QUEST`, and `MONTHLY_QUEST` are strict-cycle quests: `grace_period_days` and `submission_deadline_days` are ignored and normalized to `0`.
- Use grace/submission deadline fields only for non-cycle quests such as limited events, main quests, side quests, promotion quests, hidden quests, and repeatable quests.
