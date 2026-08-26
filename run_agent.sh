while true; do
  opencode run -m deepseek/deepseek-v4-flash "Read TASKS.md and AGENT_STATE.md. Work on the next incomplete task. Update AGENT_STATE.md with what changed, test results, blockers, and the next recommended step. Stop after one coherent unit of work."
  sleep 5
done
