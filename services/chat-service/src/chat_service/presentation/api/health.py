from fastapi import APIRouter, HTTPException, Request, status

router = APIRouter()

@router.get("/health")
async def health():
    return {"status": "ok"}

@router.get("/health/live")
async def live():
    return {"status": "ok"}

@router.get("/health/ready")
async def ready(request: Request):
    
    from sqlalchemy import text
    session_factory = request.app.state.session_factory
    async with session_factory() as s:
        await s.execute(text("SELECT 1"))
    await request.app.state.redis.ping()
    chat_events = getattr(request.app.state, "chat_events", None)
    if chat_events is not None and not await chat_events.ping():
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="RabbitMQ unavailable",
        )
    return {"status": "ready"}