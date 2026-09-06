create table Categories (
    id integer primary key autoincrement,
    name text not null,
    color integer not null default 0,
    position integer not null default 0
);

alter table Habits add column category_id integer references Categories(id);