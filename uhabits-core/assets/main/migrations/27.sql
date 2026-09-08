create table TaskCategories (
    id integer primary key autoincrement,
    name text not null,
    color integer not null default 0,
    position integer not null default 0
);

create table Tasks (
    id integer primary key autoincrement,
    title text not null,
    description text,
    category_id integer references TaskCategories(id),
    due_date integer,
    reminder_time integer,
    completed integer not null default 0,
    position integer not null default 0
);
