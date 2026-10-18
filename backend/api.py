"""Pencils & Stencils REST API.

Local: install requirements, then run `python -m uvicorn api:app --reload --port 8000`.
Android emulator URL: http://10.0.2.2:8000/
For Azure, set DATABASE_URL to a PostgreSQL connection string using the psycopg driver.
"""
from __future__ import annotations

import hashlib
import hmac
import os
import re
import secrets
import time
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel
from sqlalchemy import BigInteger, ForeignKey, Integer, LargeBinary, String, create_engine, delete, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker

ROOT = Path(__file__).resolve().parent
DEFAULT_DB = ROOT / "pencils_stencils_api.db"
DATABASE_URL = os.environ.get("DATABASE_URL", f"sqlite:///{DEFAULT_DB.as_posix()}")
ITERATIONS = 310_000
TOKEN_LIFETIME = 60 * 60 * 24 * 30

PRODUCTS = [
    (1, "Royal Ink Technical Pen", "Precision 0.5 mm nib for fine line work.", 45000, "Pens"),
    (2, "Studio Graphite Set", "Six soft graphite grades for sketching.", 18900, "Pencils"),
    (3, "Watercolour Pocket Palette", "Twelve vivid colours in a travel case.", 32500, "Paint"),
    (4, "Canvas Sketchbook", "A sturdy 120-page book for daily ideas.", 24900, "Paper"),
    (5, "Precision Ruler Set", "Three clear rulers for accurate layouts.", 12900, "Tools"),
    (6, "Charcoal Artist Sticks", "Rich charcoal for expressive shading.", 9900, "Pencils"),
    (7, "Fine Liner Trio", "Archival black liners in three tip sizes.", 15900, "Pens"),
    (8, "Acrylic Starter Set", "Six blendable acrylic colours.", 29900, "Paint"),
    (9, "Creative Stencil Pack", "Reusable geometric and botanical shapes.", 13900, "Tools"),
    (10, "Mixed Media Pad", "Heavyweight sheets for paint and ink.", 21900, "Paper"),
]


class Base(DeclarativeBase):
    pass


class ProductRow(Base):
    __tablename__ = "products"
    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    description: Mapped[str] = mapped_column(String(500), nullable=False)
    price_cents: Mapped[int] = mapped_column(Integer, nullable=False)
    category: Mapped[str] = mapped_column(String(40), nullable=False)


class UserRow(Base):
    __tablename__ = "users"
    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    name: Mapped[str] = mapped_column(String(120), nullable=False)
    email: Mapped[str] = mapped_column(String(254), nullable=False, unique=True, index=True)
    salt: Mapped[bytes] = mapped_column(LargeBinary(16), nullable=False)
    password_hash: Mapped[bytes] = mapped_column(LargeBinary(32), nullable=False)
    created_at: Mapped[int] = mapped_column(BigInteger, nullable=False)


class SessionRow(Base):
    __tablename__ = "sessions"
    token_hash: Mapped[str] = mapped_column(String(64), primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), nullable=False, index=True)
    expires_at: Mapped[int] = mapped_column(BigInteger, nullable=False, index=True)


connect_args = {"check_same_thread": False} if DATABASE_URL.startswith("sqlite:") else {}
engine = create_engine(DATABASE_URL, pool_pre_ping=True, connect_args=connect_args)
DbSession = sessionmaker(bind=engine, expire_on_commit=False)
app = FastAPI(title="Pencils & Stencils API", version="1.0.0")


class RegisterBody(BaseModel):
    name: str
    email: str
    password: str


class LoginBody(BaseModel):
    email: str
    password: str


def password_hash(password: str, salt: bytes) -> bytes:
    return hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, ITERATIONS, dklen=32)


def initialize_database() -> None:
    Base.metadata.create_all(engine)
    now = int(time.time())
    with DbSession() as db:
        for product in PRODUCTS:
            if db.get(ProductRow, product[0]) is None:
                db.add(ProductRow(id=product[0], name=product[1], description=product[2], price_cents=product[3], category=product[4]))
        # Demo records provide usable sample data; replace/remove them before production.
        for index in range(1, 11):
            email = f"demo{index:02d}@example.test"
            exists = db.scalar(select(UserRow.id).where(UserRow.email == email))
            if exists is None:
                salt = secrets.token_bytes(16)
                db.add(UserRow(name=f"Demo Customer {index:02d}", email=email, salt=salt,
                               password_hash=password_hash("DemoPass123!", salt), created_at=now))
        db.commit()


@app.on_event("startup")
def startup() -> None:
    initialize_database()


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.get("/api/products")
def list_products() -> list[dict]:
    with DbSession() as db:
        products = db.scalars(select(ProductRow).order_by(ProductRow.id)).all()
        return [{"id": p.id, "name": p.name, "description": p.description,
                 "price_cents": p.price_cents, "category": p.category} for p in products]


def new_session(db, user: UserRow) -> dict:
    token = secrets.token_urlsafe(32)
    expires = int(time.time()) + TOKEN_LIFETIME
    db.add(SessionRow(token_hash=hashlib.sha256(token.encode()).hexdigest(), user_id=user.id, expires_at=expires))
    db.commit()
    return {"token": token, "expires_at": expires,
            "user": {"id": user.id, "name": user.name, "email": user.email}}


@app.post("/api/auth/register", status_code=201)
def register(body: RegisterBody) -> dict:
    name = body.name.strip()
    email = body.email.strip().lower()
    if len(name) < 2:
        raise HTTPException(status_code=400, detail="Enter your name")
    if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email):
        raise HTTPException(status_code=400, detail="Enter a valid email address")
    if len(body.password) < 8:
        raise HTTPException(status_code=400, detail="Password must be at least 8 characters")
    salt = secrets.token_bytes(16)
    with DbSession() as db:
        user = UserRow(name=name, email=email, salt=salt,
                       password_hash=password_hash(body.password, salt), created_at=int(time.time()))
        db.add(user)
        try:
            db.flush()
        except IntegrityError:
            db.rollback()
            raise HTTPException(status_code=409, detail="An account with that email already exists")
        return new_session(db, user)


@app.post("/api/auth/login")
def login(body: LoginBody) -> dict:
    email = body.email.strip().lower()
    with DbSession() as db:
        user = db.scalar(select(UserRow).where(UserRow.email == email))
        if user is None or not hmac.compare_digest(password_hash(body.password, user.salt), user.password_hash):
            raise HTTPException(status_code=401, detail="Email or password is incorrect")
        return new_session(db, user)


def authenticated_user(db, authorization: str | None) -> UserRow | None:
    scheme, _, token = (authorization or "").partition(" ")
    if scheme.lower() != "bearer" or not token:
        return None
    token_hash = hashlib.sha256(token.encode()).hexdigest()
    session = db.get(SessionRow, token_hash)
    if session is None or session.expires_at <= int(time.time()):
        return None
    return db.get(UserRow, session.user_id)


@app.get("/api/me")
def current_user(authorization: str | None = Header(default=None)) -> dict:
    with DbSession() as db:
        user = authenticated_user(db, authorization)
        if user is None:
            raise HTTPException(status_code=401, detail="Sign in is required")
        return {"id": user.id, "name": user.name, "email": user.email}


@app.post("/api/auth/logout")
def logout(authorization: str | None = Header(default=None)) -> dict:
    scheme, _, token = (authorization or "").partition(" ")
    if scheme.lower() == "bearer" and token:
        token_hash = hashlib.sha256(token.encode()).hexdigest()
        with DbSession() as db:
            db.execute(delete(SessionRow).where(SessionRow.token_hash == token_hash))
            db.commit()
    return {"status": "signed_out"}
